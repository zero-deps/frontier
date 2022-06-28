package ftier.demo

import ftier.*, ws.*, http.*, server.*
import ftier.nio.*, ftier.nio.core.*
import zio.*

object Demo extends ZIOAppDefault:
  def run =
    for
      addr <- SocketAddress.inetSocketAddress(9012).orDie
      _ <- bind(addr, httpHandler, ServerConf(workers=10))
    yield ()

val httpHandler: HttpHandler[Any] =
  case UpgradeRequest(r) if r.req.path == "/wsecho" =>
    ZIO.succeed(WsResp(r, wsHandler))
  case req@ Post(Root / "echo") =>
    ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(req.bodyAsBytes) ++ Chunk.fromArray("\n".getBytes.nn))))
  case _ =>
    ZIO.succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => ZIO.unit
