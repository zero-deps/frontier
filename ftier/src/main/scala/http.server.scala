package ftier
package http
package server

import zio.*, nio.*, core.*, core.channels.*, stream.*
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

def processHttp[R](ch: SocketChannel, h: HttpHandler[R])(protocol: Protocol.Http, chunk: Chunk[Byte]): RIO[R, Protocol] =
  http.processChunk(chunk, protocol.state).flatMap{
    case HttpState.MsgDone(meta, body) =>
      val req = Request(method=meta.method, url=meta.url, meta.headers, body)
      for
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
                      _ <- ZIO.whenZIO(ch.isConnected){ ch.write(bb) }.orDie
                    yield ()
                  , status => for
                      bb <- write(Close(status)).orDie
                      _ <- ZIO.whenZIO(ch.isConnected){ ch.write(bb) }.orDie
                      _ <- ZIO.whenZIO(ch.isConnected){ ch.close }.orDie
                    yield ()                
                  , java.util.UUID.randomUUID().toString
                  ),
                  x.handler
                )
              upgrade(x.req).map(resp => (p, Some(resp)))
            case x: Response =>
              val p = Protocol.http
              ZIO.succeed((p, Some(x)))
      yield res
    case s => 
      ZIO.succeed((protocol.copy(state=s), None))
  }.catchAll{
    case BadReq => ZIO.succeed((Protocol.http, Some(Response.empty(400))))
    case e: Throwable => ZIO.fail(e)
  }.tap{
    case (p, Some(Response(code@ 101, headers, _))) =>
      ch.writeChunk(buildRe(code, headers))

    case (p, Some(Response(code, headers, BodyChunk(body)))) =>
      ch.writeChunk(buildRe(code, headers)) *> ch.writeChunk(body) *> ch.close
    
    case (p, Some(Response(code, headers, BodyStream(body)))) =>
      for
        _ <- ch.writeChunk(buildRe(code, headers :+ ("Transfer-Encoding" -> "chunked")))
        _ <-
          body.runForeachChunk(x =>
            for
              _ <- ch.writeChunk(Chunk.fromArray(x.size.toHexString.getBytes("ascii").nn))
              _ <- ch.writeChunk(Chunk.fromArray("\r\n".getBytes("ascii").nn))
              _ <- ch.writeChunk(x)
              _ <- ch.writeChunk(Chunk.fromArray("\r\n".getBytes("ascii").nn))
            yield ()
          )
        _ <- ch.writeChunk(Chunk.fromArray("0\r\n\r\n".getBytes("ascii").nn))
        _ <- ch.close
      yield ()
    
    case (p, None) => ZIO.unit
  }.map(_._1).flatMap{
    case p: Protocol.Http => ZIO.succeed(p)
    case p: Protocol.Ws[R] =>
      val ctx: ULayer[WsContext] = ZLayer.succeed(p.ctx)
      for
        _ <- p.handler(Open).provideSomeLayer[R](ctx)
      yield p
  }

def processWs[R](ch: SocketChannel)(protocol: Protocol.Ws[R], chunk: Chunk[Byte]): RIO[R, Protocol] =
  val state = protocol.state
  val newState = ws.parseHeader(state.copy(data=state.data ++ chunk))
  newState match
    case WsState(Some(h: WsHeader), chunk, fragmentsOpcode) if h.size <= chunk.length =>
      val (data, rem) = chunk.splitAt(h.size)
      val payload = processMask(h.mask, h.maskN, data)
      val ctx: ULayer[WsContext] = ZLayer.succeed(protocol.ctx)
      for
        msg <- read(h.opcode, payload, h.fin, fragmentsOpcode)
        _ <- protocol.handler(msg).provideSomeLayer[R](ctx)
        r <- processWs(ch)(protocol.copy(state=WsState(None, rem, fragmentsOpcode)), Chunk.empty)
      yield r
    case state => 
      ZIO.succeed(protocol.copy(state=state))

def httpProtocol[R](ch: SocketChannel, h: HttpHandler[R], state: Ref[Protocol])(chunk: Chunk[Byte]): RIO[R, Unit] =
  for
    data <- state.get
    data <-
      data match
        case p: Protocol.Http => processHttp(ch, h)(p, chunk)
        case p: Protocol.Ws[R] => processWs(ch)(p, chunk)
    _ <- state.set(data)
  yield ()

def bind[R](addr: SocketAddress, h: HttpHandler[R], conf: ServerConf = ServerConf.default): RIO[R, Unit] =
  for
    r <- ZIO.environment[R]
    _ <- tcp.bind(addr, conf.workers, ch =>
      for
        state <- Ref.make[Protocol](Protocol.http)
      yield httpProtocol(ch, h, state)(_).provideEnvironment(r)
    )
  yield ()
