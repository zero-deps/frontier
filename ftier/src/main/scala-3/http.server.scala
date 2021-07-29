package ftier
package http
package server

import zio.*, nio.*, core.*, core.channels.*
import ws.*

import ext.{*, given}

sealed trait Protocol
case class Http(state: HttpState) extends Protocol
case class WsProto(state: WsState, ctx: WsContextData) extends Protocol

object Protocol {
  def http: Http = Http(HttpState())
  def ws(ctx: WsContextData): WsProto = WsProto(WsState(None, Chunk.empty), ctx)
}

type HttpHandler[R] = Request => RIO[R, Response]
type WsHandler[R] = Msg => RIO[WsContext & R, Unit]

def processHttp[R <: Has[?]](
  ch: SocketChannel
, hth: HttpHandler[R]
, wsh: WsHandler[R]
)(
  protocol: Http
, chunk: Chunk[Byte]
): RIO[R, Protocol] =
  val x1: IO[BadReq.type, HttpState] =
    http.processChunk(chunk, protocol.state)
  val x2: ZIO[R, BadReq.type | Throwable, (Protocol, Option[Response])] =
    x1.flatMap{
      case s: MsgDone =>
        for {
          req  <- toReq(s.msg)
          resp <- hth(req)
        } yield {
          if (resp.code == 101) {
            val p =
              Protocol.ws(
                WsContextData(
                  req
                , msg => for {
                    bb <- write(msg).orDie
                    _ <- ch.write(bb).orDie
                  } yield ()
                , ch.close
                )
              )
            (p, Some(resp))
          } else {
            val p = Protocol.http
            (p, Some(resp))
          }
        }
      case s => 
        IO.succeed((protocol.copy(state=s), None))
    }
  val x3: RIO[R, (Protocol, Option[Response])] = x2.catchAll{
    case BadReq => IO.succeed((Protocol.http, Some(Response(400, Nil, Chunk.empty))))
    case e: Throwable => IO.fail(e)
  }
  x3.flatMap{ 
    case (p, Some(resp)) if resp.code == 101 =>
      ch.write(build(resp)) *> IO.succeed(p)
    case (p, Some(resp)) =>
      ch.write(build(resp)) *> ch.close *> IO.succeed(p)
    case (p, None) =>
      IO.succeed(p)
  }.flatMap{
    case p: Http => IO.succeed(p)
    case p: WsProto =>
      val ctx: ULayer[WsContext] = ZLayer.succeed(p.ctx)
      for {
        _ <- wsh(Open).provideSomeLayer[R](ctx)
      } yield p
  }

def processWs[R <: Has[?]](
  ch: SocketChannel
, wsh: WsHandler[R]
)(
  protocol: WsProto
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
        _ <- wsh(msg).provideSomeLayer[R](ctx)
        r <- processWs(ch, wsh)(protocol.copy(state=WsState(None, rem)), Chunk.empty)
      } yield r
    case state => 
      IO.succeed(protocol.copy(state=state))

def httpProtocol[R <: Has[?]](
  ch: SocketChannel
, hth: HttpHandler[R]
, wsh: WsHandler[R]
, state: Ref[Protocol]
)(
  chunk: Chunk[Byte]
): RIO[R, Unit] =
  for {
    data <- state.get
    data <-
      data match
        case p: Http => processHttp(ch, hth, wsh)(p, chunk)
        case p: WsProto => processWs(ch, wsh)(p, chunk)
    _ <- state.set(data)
  } yield ()

def bind[R <: Has[?]](
  addr: SocketAddress
, hth: HttpHandler[R]
, wsh: WsHandler[R]
): RIO[R, Unit] =
  for {
    r <- ZIO.environment[R]
    _ <- tcp.bind(addr, 1, ch =>
      for {
        state <- Ref.make[Protocol](Protocol.http)
        h = (x: Request) => hth(x)
        w = (x: Msg) => wsh(x)
      } yield httpProtocol(ch, h, w, state)(_).provide(r)
    )
  } yield ()
