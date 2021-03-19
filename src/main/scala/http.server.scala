package ftier
package http
package server

import zio.*, nio.*, core.*, core.channels.*

import http.*, ws.*

sealed trait Protocol
object Protocol {
  def http: Http = Http(HttpState())
  def ws(ctx: WsContextData): Ws = Ws(new WsState(None, Chunk.empty), ctx)
}
case class Http(state: HttpState) extends Protocol
case class Ws(state: WsState, ctx: WsContextData) extends Protocol

type HttpHandler = Request => ZIO[Any, Nothing, Response]
type WsHandler = Msg => ZIO[WsContext, Nothing, Unit]

def processHttp(
  channel: SocketChannel
, handler: HttpHandler
, handler2: WsHandler
)(
  protocol: Http
, chunk: Chunk[Byte]
): Task[Protocol] = {
  val x1: IO[BadContentLength.type, HttpState] =
    http.processChunk(chunk, protocol.state)
  val x2: IO[BadContentLength.type | BadFirstLine.type, (Protocol, Option[Response])] =
    x1.flatMap{
      case s: MsgDone =>
        for {
          req  <- toReq(s.msg)
          resp <- handler(req)
        } yield {
          if (resp.code == 101) {
            val writeF: Msg => IO[WriteErr, Unit] = msg => write(msg).mapError(WriteErr(_)).flatMap(channel.write(_).unit.orDie)
            val p = Protocol.ws(WsContextData(req, writeF, channel.close))
            (p, Some(resp))
          } else {
            val p = Protocol.http
            (p, Some(resp))
          }
        }
      case s => 
        IO.succeed((protocol.copy(state=s), None))
    }
  val x3 = x2.catchAll{ (x: BadContentLength.type | BadFirstLine.type) => x match
    case BadContentLength => IO.succeed((Protocol.http, Some(Response(400))))
    case BadFirstLine => IO.succeed((Protocol.http, Some(Response(400))))
  }
  x3.flatMap{ 
    case (p, Some(resp)) if resp.code == 101 =>
      channel.write(build(resp)) *> IO.succeed(p)
    case (p, Some(resp)) =>
      channel.write(build(resp)) *> channel.close *> IO.succeed(p)
    case (p, None) =>
      IO.succeed(p)
  }.flatMap{
    case p: Http => IO.succeed(p)
    case p: Ws   =>
      val ctx: ULayer[WsContext] = ZLayer.succeed(p.ctx)
      handler2(Open).provideLayer(ctx).catchAllCause(err =>
        IO.effect(println(s"ws err ${err.prettyPrint}"))
      ).ignore *> IO.succeed(p)
  }
}

def processWs(channel: SocketChannel, handler: WsHandler)(protocol: Ws, chunk: Chunk[Byte]): Task[Protocol] = {
  val state = protocol.state
  val newState = ws.parseHeader(state.copy(data=state.data ++ chunk))
  newState match {
    case WsState(Some(h: WsHeader), chunk) if h.size <= chunk.length =>
      val (data, rem) = chunk.splitAt(h.size)
      val payload = processMask(h.mask, h.maskN, data)
      val msg = read(h.opcode, payload)
      val ctx: ULayer[WsContext] = ZLayer.succeed(protocol.ctx)
      handler(msg).provideLayer(ctx).catchAllCause(err =>
          IO.effect(println(s"ws err ${err.prettyPrint}"))
      ).ignore *> processWs(channel, handler)(protocol.copy(state=WsState(None, rem)), Chunk.empty)
    case state => 
      IO.succeed(protocol.copy(state=state))
  }
}

def httpProtocol(channel: SocketChannel, h1: HttpHandler, h2: WsHandler, state: Ref[Protocol])(chunk: Chunk[Byte]): IO[Throwable, Unit] = for {
  data <- state.get
  data <- data match {
            case p: Http => processHttp(channel, h1, h2)(p, chunk)
            case p: Ws   => processWs(channel, h2)(p, chunk)
          }
  _    <- state.set(data)
} yield ()

def bind(addr: SocketAddress, handler1: UIO[HttpHandler], handler2: UIO[WsHandler]): Task[Unit] = {
  tcp.bind(addr, 1, channel =>
    for {
      state <- Ref.make[Protocol](Protocol.http)
      h1 <- handler1
      h2 <- handler2
    } yield httpProtocol(channel, h1, h2, state)(_)
  )
}
