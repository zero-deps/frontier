package benchmark
package ziohttp

import zio.*, http.*, Method.GET, ChannelEvent.Read

object ZiohttpWs extends ZIOAppDefault:
  val run =
    Server.serve(
      Routes(GET / conf.path -> handler(socketApp.toResponse))
    ).provide(Server.defaultWithPort(conf.port))

val socketApp: WebSocketApp[Any] =
  Handler.webSocket: channel =>
    channel.receiveAll:
      case x @ Read(WebSocketFrame.Binary(_)) =>
        channel.send(x)
      case _ =>
        ZIO.unit
