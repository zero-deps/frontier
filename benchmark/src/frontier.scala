package benchmark
package frontier

import ftier.*, ws.*, http.*, server.*
import java.net.InetSocketAddress
import zio.ZIOAppDefault
import zio.ZIO.{attempt, succeed, unit}

object FtierWs extends ZIOAppDefault:
  val run =
    for
      addr <- attempt(InetSocketAddress(conf.port)).orDie
      _ <- bind(addr, httpHandler, ServerConf(workers=10))
    yield ()

val httpHandler: HttpHandler[Any] =
  case UpgradeRequest(r) if r.req.path == s"/${conf.path}" =>
    succeed(WsResp(r, wsHandler))
  case _ =>
    succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => unit
