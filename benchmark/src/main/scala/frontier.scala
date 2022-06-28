package ftier.benchmark

import ftier.*, ws.*, http.*, server.*
import ftier.nio.*, ftier.nio.core.*
import zio.*

object Benchmark extends ZIOAppDefault:
  def run =
    for
      addr <- SocketAddress.inetSocketAddress(9011).orDie
      _ <- bind(addr, httpHandler, ServerConf(workers=10))
    yield ()

val httpHandler: HttpHandler[Any] =
  case UpgradeRequest(r) if r.req.path == "/wsecho" =>
    ZIO.succeed(WsResp(r, wsHandler))
  case _ =>
    ZIO.succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => ZIO.unit
