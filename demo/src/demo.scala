package ftier.demo

import ftier.*, ws.*, http.*, server.*
import zio.*, nio.*

val app =
  val addr = SocketAddress.fromJava(java.net.InetSocketAddress(9012))
  for
    _ <- bind(addr, httpHandler, ServerConf(workers=10))
  yield ()

@main def run(): Unit = Unsafe.unsafe(Runtime.default.unsafe.run(app))

val httpHandler: HttpHandler[Any] =
  case UpgradeRequest(r) if r.req.path == "/wsecho" =>
    ZIO.succeed(WsResp(r, wsHandler))
  case req@ Post(Root / "echo") =>
    ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(req.bodyAsBytes))))
  case _ =>
    ZIO.succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => ZIO.unit
