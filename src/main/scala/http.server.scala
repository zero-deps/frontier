package ftier
package http
package server

import zio.*, nio.*, core.*, core.channels.*
import zero.ext.*, option.*

import ws.*

sealed trait Protocol
case class Http(state: HttpState) extends Protocol
case class Ws(state: WsState, ctx: WsContextData) extends Protocol

object Protocol {
  def http: Http = Http(HttpState())
  def ws(ctx: WsContextData): Ws = Ws(WsState(None, Chunk.empty), ctx)
}

type HttpHandler = Request => ZIO[Any, Nothing, Response]
type WsHandler = Msg => ZIO[WsContext, Nothing, Unit]

def processHttp(
  ch: SocketChannel
, hth: HttpHandler
, wsh: WsHandler
)(
  protocol: Http
, chunk: Chunk[Byte]
): Task[Protocol] =
  val x1: IO[BadReq.type, HttpState] =
    http.processChunk(chunk, protocol.state)
  val x2: IO[BadReq.type | BadReq.type, (Protocol, Option[Response])] =
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
                  } yield unit
                , ch.close
                )
              )
            (p, resp.some)
          } else {
            val p = Protocol.http
            (p, resp.some)
          }
        }
      case s => 
        IO.succeed((protocol.copy(state=s), None))
    }
  val x3 = x2.catchAll{
    case BadReq => IO.succeed((Protocol.http, Response(400).some))
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
    case p: Ws   =>
      val ctx: ULayer[WsContext] = ZLayer.succeed(p.ctx)
      wsh(Open).provideLayer(ctx).catchAllCause(err =>
        IO.effect(println(s"ws err ${err.prettyPrint}")) //todo: ?
      ).ignore *> IO.succeed(p)
  }

def processWs(
  ch: SocketChannel
, handler: WsHandler
)(
  protocol: Ws
, chunk: Chunk[Byte]
): Task[Protocol] =
  val state = protocol.state
  val newState = ws.parseHeader(state.copy(data=state.data ++ chunk))
  newState match
    case WsState(Some(h: WsHeader), chunk) if h.size <= chunk.length =>
      val (data, rem) = chunk.splitAt(h.size)
      val payload = processMask(h.mask, h.maskN, data)
      val msg = read(h.opcode, payload)
      val ctx: ULayer[WsContext] = ZLayer.succeed(protocol.ctx)
      handler(msg).provideLayer(ctx).catchAllCause(err =>
          IO.effect(println(s"ws err ${err.prettyPrint}"))
      ).ignore *> processWs(ch, handler)(protocol.copy(state=WsState(None, rem)), Chunk.empty)
    case state => 
      IO.succeed(protocol.copy(state=state))

def httpProtocol(
  ch: SocketChannel
, h1: HttpHandler
, h2: WsHandler
, state: Ref[Protocol]
)(
  chunk: Chunk[Byte]
): IO[Throwable, Unit] =
  for {
    data <- state.get
    data <-
      data match
        case p: Http => processHttp(ch, h1, h2)(p, chunk)
        case p: Ws => processWs(ch, h2)(p, chunk)
    _ <- state.set(data)
  } yield unit

def bind(
  addr: SocketAddress
, hth: UIO[HttpHandler]
, wsh: UIO[WsHandler]
): Task[Unit] =
  tcp.bind(addr, 1, ch =>
    for {
      state <- Ref.make[Protocol](Protocol.http)
      h1 <- hth
      h2 <- wsh
    } yield httpProtocol(ch, h1, h2, state)
  )
