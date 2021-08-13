package ftier
package http
package server

import zio.*, nio.*, core.*, core.channels.*, stream.*, blocking.*
import ws.*

import ext.{*, given}

sealed trait Protocol
case class Http(state: HttpState) extends Protocol
case class WsProto[R](state: WsState, ctx: WsContextData, handler: WsHandler[R]) extends Protocol

object Protocol {
  def http: Http = Http(HttpState())
  def ws[R](ctx: WsContextData, handler: WsHandler[R]): WsProto[R] = WsProto(WsState(None, Chunk.empty), ctx, handler)
}

type HttpHandler[R] = Request => RIO[R, Resp]
type WsHandler[R] = Msg => RIO[WsContext & R, Unit]

sealed trait Resp
case class HttpResp(resp: Response) extends Resp
case class WsResp[R](req: UpgradeRequest, handler: WsHandler[R]) extends Resp

def processHttp[R <: Has[?]](
  ch: SocketChannel
, h: HttpHandler[R]
)(
  protocol: Http
, chunk: Chunk[Byte]
): RIO[R & Blocking, Protocol] =
  val x1: IO[BadReq.type, HttpState] =
    http.processChunk(chunk, protocol.state)
  val x2: ZIO[R, BadReq.type | Throwable, (Protocol, Option[Response])] =
    x1.flatMap{
      case s: MsgDone =>
        for {
          req  <- toReq(s.msg)
          resp <- h(req)
          res <- resp match
            case x: WsResp[R] =>
              val p =
                Protocol.ws(
                  WsContextData(
                    req
                  , msg => for {
                      bb <- write(msg).orDie
                      _ <- ch.write(bb).orDie
                    } yield ()
                  , ch.close
                  , java.util.UUID.randomUUID().toString
                  ),
                  x.handler
                )
              upgrade(x.req).map(resp => (p, Some(resp)))
            case x: HttpResp =>
              val p = Protocol.http
              IO.succeed((p, Some(x.resp)))
        } yield res
      case s => 
        IO.succeed((protocol.copy(state=s), None))
    }
  val x3: RIO[R, (Protocol, Option[Response])] = x2.catchAll{
    case BadReq => IO.succeed((Protocol.http, Some(Response.empty(400, Nil))))
    case e: Throwable => IO.fail(e)
  }
  val x4: RIO[R & Blocking, Protocol] = x3.tap{ 
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
  }.map(_._1)
  x4.flatMap{
    case p: Http => IO.succeed(p)
    case p: WsProto[R] =>
      val ctx: ULayer[WsContext] = ZLayer.succeed(p.ctx)
      for {
        _ <- p.handler(Open).provideSomeLayer[R](ctx)
      } yield p
  }

def processWs[R <: Has[?]](
  ch: SocketChannel
)(
  protocol: WsProto[R]
, chunk: Chunk[Byte]
): RIO[R, Protocol] =
  val state = protocol.state
  val newState = ws.parseHeader(state.copy(data=state.data ++ chunk))
  newState match
    case WsState(Some(h: WsHeader), chunk) if h.size <= chunk.length =>
      val (data, rem) = chunk.splitAt(h.size)
      val payload = processMask(h.mask, h.maskN, data)
      val msg = read(h.opcode, payload)
      val ctx: ULayer[WsContext] = ZLayer.succeed(protocol.ctx)
      for {
        _ <- protocol.handler(msg).provideSomeLayer[R](ctx)
        r <- processWs(ch)(protocol.copy(state=WsState(None, rem)), Chunk.empty)
      } yield r
    case state => 
      IO.succeed(protocol.copy(state=state))

def httpProtocol[R <: Has[?]](
  ch: SocketChannel
, h: HttpHandler[R]
, state: Ref[Protocol]
)(
  chunk: Chunk[Byte]
): RIO[R & Blocking, Unit] =
  for {
    data <- state.get
    data <-
      data match
        case p: Http => processHttp(ch, h)(p, chunk)
        case p: WsProto[R] => processWs(ch)(p, chunk)
    _ <- state.set(data)
  } yield ()

def bind[R <: Has[?]](
  addr: SocketAddress
, h: HttpHandler[R]
): RIO[R & Blocking, Unit] =
  for {
    r <- ZIO.environment[R & Blocking]
    _ <- tcp.bind(addr, 1, ch =>
      for {
        state <- Ref.make[Protocol](Protocol.http)
        hh = (x: Request) => h(x)
      } yield httpProtocol(ch, hh, state)(_).provide(r)
    )
  } yield ()
