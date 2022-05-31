package ftier.demo

import ftier.*, ws.*, http.*, server.*
import zio.*, nio.*, core.*

val app =
  for
    addr <- SocketAddress.inetSocketAddress(9012).orDie
    _ <- bind(addr, httpHandler, ServerConf(workers=10))
  yield ()

@main def run(): Unit = Runtime.default.unsafeRun(app)

val httpHandler: HttpHandler[ZEnv] =
  case UpgradeRequest(r) if r.req.path == "/wsecho" =>
    IO.succeed(WsResp(r, wsHandler))
  case req@ Post(Root / "echo") =>
    IO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(req.bodyAsBytes))))
  case _ =>
    IO.succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext & ZEnv] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => IO.unit
