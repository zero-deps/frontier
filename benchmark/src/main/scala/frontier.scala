package ftier.benchmark

import ftier.*, ws.*, http.*, server.*
import zio.*, nio.*, core.*

val app =
  for
    addr <- SocketAddress.inetSocketAddress(9011).orDie
    _ <- bind(addr, httpHandler, ServerConf(workers=10))
  yield ()

@main def run(): Unit = Runtime.default.unsafeRun(app)

val httpHandler: HttpHandler[ZEnv] =
  case UpgradeRequest(r) if r.req.path == "/wsecho" =>
    ZIO.succeed(WsResp(r, wsHandler))
  case _ =>
    ZIO.succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext & ZEnv] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => ZIO.unit
