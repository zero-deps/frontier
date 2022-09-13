package ftier.demo

import ftier.*, ws.*, http.*, server.*
import java.net.InetSocketAddress
import zio.*

object Demo extends ZIOAppDefault:
  def run =
    for
      addr <- ZIO.attempt(InetSocketAddress(9012)).orDie
      httpServer <- bind(addr, httpHandler, ServerConf(workers=10)).fork
      body <- ZIO.succeed("こんにちは")
      _ <- Console.printLine(s"TRACE http://localhost:9012 '$body'")
      httpClient <- http.client.httpClient
      r <-
        http.client.sendAsync(
          httpClient
        , Request("TRACE", "http://localhost:9012", Map.empty, BodyChunk(Chunk.fromArray(body.getBytes.nn)))
        )
      _ <- Console.print(s"200 OK '${r.bodyAsString}'")
      _ <- httpServer.join
    yield ()

val `\n` = Chunk.fromArray("\n".getBytes.nn)
def shuffle(xs: Array[Byte]): Array[Byte] =
  String(util.Random.shuffle(xs.asString.toArray.toList).toArray).getBytes.nn

val httpHandler: HttpHandler[Any] =
  case UpgradeRequest(r) if r.req.path == "/wsecho" =>
    ZIO.succeed(WsResp(r, wsHandler))
  case req@ Get(Root / "echo") =>
    ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(shuffle(req.bodyAsBytes)) ++ `\n`)))
  case req@ Post(Root / "echo") =>
    ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(shuffle(req.bodyAsBytes)) ++ `\n`)))
  case req@ Request("TRACE", _, _, _) =>
    ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(shuffle(req.bodyAsBytes)) ++ `\n`)))
  case _ =>
    ZIO.succeed(Response.empty(404))

val wsHandler: WsHandler[WsContext] =
  case msg: Binary => Ws.send(msg)
  case _: Close => Ws.close()
  case _ => ZIO.unit
