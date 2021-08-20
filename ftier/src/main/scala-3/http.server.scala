package ftier
package http
package server

import zio.*, nio.*, core.*, core.channels.*, stream.*, blocking.*
import ws.*

import ext.{*, given}

enum Protocol:
  case Http(state: HttpState)
  case Ws[R](state: WsState, ctx: WsContextData, handler: WsHandler[R])

object Protocol:
  def http: Protocol.Http = Protocol.Http(HttpState())
  def ws[R](ctx: WsContextData, handler: WsHandler[R]): Protocol.Ws[R] = Protocol.Ws(WsState(None, Chunk.empty, None), ctx, handler)

type HttpHandler[R] = Request => RIO[R, Response | WsResp[R]]

case class WsResp[R](req: UpgradeRequest, handler: WsHandler[R])

type WsHandler[R] = Msg => RIO[WsContext & R, Unit]

def processHttp[R <: Has[?]](ch: SocketChannel, h: HttpHandler[R])(protocol: Protocol.Http, chunk: Chunk[Byte]): RIO[R & Blocking, Protocol] =
  http.processChunk(chunk, protocol.state).flatMap{
    case s: HttpState.MsgDone =>
      for
        req <- toReq(s.msg)
        resp <- h(req)
        res <-
          resp match
            case x: WsResp[R] =>
              val p =
                Protocol.ws(
                  WsContextData(
                    req
                  , msg => for
                      bb <- write(msg).orDie
                      _ <- ch.write(bb).orDie
                    yield unit
                  , ch.close
                  , java.util.UUID.randomUUID().toString
                  ),
                  x.handler
                )
              upgrade(x.req).map(resp => (p, Some(resp)))
            case x: Response =>
              val p = Protocol.http
              IO.succeed((p, Some(x)))
      yield res
    case s => 
      IO.succeed((protocol.copy(state=s), None))
  }.catchAll{
    case BadReq => IO.succeed((Protocol.http, Some(Response.empty(400, Nil))))
    case e: Throwable => IO.fail(e)
  }.tap{
    case (p, Some(Response(code@ 101, headers, None))) =>
      ch.write(buildRe(code, headers))
    
    case (p, Some(Response(code, headers, None))) =>
      ch.write(buildRe(code, headers)) *> ch.close
    
    case (p, Some(Response(code, headers, Some((body, onError, onSuccess))))) =>
      for
        _ <- ch.write(buildRe(code, headers :+ ("Transfer-Encoding" -> "chunked")))
        _ <-
          body.foreachChunk(x =>
            for
              _ <- ch.write(Chunk.fromArray(x.size.toHexString.getBytes("ascii").nn))
              _ <- ch.write(Chunk.fromArray("\r\n".getBytes("ascii").nn))
              _ <- ch.write(x)
              _ <- ch.write(Chunk.fromArray("\r\n".getBytes("ascii").nn))
            yield unit
          ).tapBoth(onError, onSuccess)
        _ <- ch.write(Chunk.fromArray("0\r\n\r\n".getBytes("ascii").nn))
        _ <- ch.close
      yield unit
    
    case (p, None) => IO.unit
  }.map(_._1).flatMap{
    case p: Protocol.Http => IO.succeed(p)
    case p: Protocol.Ws[R] =>
      val ctx: ULayer[WsContext] = ZLayer.succeed(p.ctx)
      for
        _ <- p.handler(Open).provideSomeLayer[R](ctx)
      yield p
  }

def processWs[R <: Has[?]](ch: SocketChannel)(protocol: Protocol.Ws[R], chunk: Chunk[Byte]): RIO[R, Protocol] =
  val state = protocol.state
  val newState = ws.parseHeader(state.copy(data=state.data ++ chunk))
  newState match
    case WsState(Some(h: WsHeader), chunk, fragmentsOpcode) if h.size <= chunk.length =>
      val (data, rem) = chunk.splitAt(h.size)
      val payload = processMask(h.mask, h.maskN, data)
      val msg = read(h.opcode, payload, h.fin, fragmentsOpcode)
      val ctx: ULayer[WsContext] = ZLayer.succeed(protocol.ctx)
      for
        _ <- protocol.handler(msg).provideSomeLayer[R](ctx)
        r <- processWs(ch)(protocol.copy(state=WsState(None, rem, fragmentsOpcode)), Chunk.empty)
      yield r
    case state => 
      IO.succeed(protocol.copy(state=state))

def httpProtocol[R <: Has[?]](ch: SocketChannel, h: HttpHandler[R], state: Ref[Protocol])(chunk: Chunk[Byte]): RIO[R & Blocking, Unit] =
  for
    data <- state.get
    data <-
      data match
        case p: Protocol.Http => processHttp(ch, h)(p, chunk)
        case p: Protocol.Ws[R] => processWs(ch)(p, chunk)
    _ <- state.set(data)
  yield unit

def bind[R <: Has[?]](addr: SocketAddress, h: HttpHandler[R]): RIO[R & Blocking, Unit] =
  for
    r <- ZIO.environment[R & Blocking]
    _ <- tcp.bind(addr, 1, ch =>
      for
        state <- Ref.make[Protocol](Protocol.http)
      yield httpProtocol(ch, h, state)(_).provide(r)
    )
  yield unit
