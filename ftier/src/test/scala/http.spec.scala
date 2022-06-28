package ftier
package http

import ftier.*, ws.*, http.*, server.*
import ftier.nio.*, ftier.nio.core.*
import zio.*, test.*, Assertion.*

object HttpSpec extends ZIOSpecDefault:
  val httpHandler: HttpHandler[Any] =
    case req@ Request("TRACE", _, _, _) =>
      ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(req.bodyAsBytes))))
    case req@ Get(Root / "echo") =>
      ZIO.succeed(Response(200, Nil, BodyChunk(Chunk.fromArray(req.bodyAsBytes))))
    case _ =>
      ZIO.succeed(Response.empty(404))

  val port = 9014
  val addr = SocketAddress.inetSocketAddress(port).orDie
  val body = "こんにちは"
  val bodyChunk = BodyChunk(Chunk.fromArray(body.getBytes.nn))
  val httpClient = http.client.httpClient

  val waitForHttpServer: Task[Unit] =
    ZIO.attempt(new java.net.Socket("localhost", port).close())
      .retry(Schedule.linear(10 milliseconds) && Schedule.recurs(300))

  def spec = suite("HttpSpec")(
    test("server answers on defined request") {
      for
        httpServer <- addr.flatMap(bind(_, httpHandler, ServerConf(workers=10)).fork)
        _ <- Live.live(waitForHttpServer)
        req <- ZIO.succeed(Request("TRACE", s"http://localhost:$port", Map.empty, bodyChunk))
        r <- httpClient.flatMap(http.client.sendAsync(_, req))
      yield assertTrue(r.bodyAsString == body)
    },
    test("server support get request with body") {
      for
        httpServer <- addr.flatMap(bind(_, httpHandler, ServerConf(workers=10)).fork)
        _ <- Live.live(waitForHttpServer)
        req <- ZIO.succeed(Request("GET", s"http://localhost:$port/echo", Map.empty, bodyChunk))
        r <- httpClient.flatMap(http.client.sendAsync(_, req))
      yield assertTrue(r.bodyAsString == body)
    },
    test("server answers on undefined request") {
      for
        httpServer <- addr.flatMap(bind(_, httpHandler, ServerConf(workers=10)).fork)
        _ <- Live.live(waitForHttpServer)
        req <- ZIO.succeed(Request("GET", s"http://localhost:$port", Map.empty, bodyChunk))
        r <- httpClient.flatMap(http.client.sendAsync(_, req))
      yield assertTrue(r.code == 404)
    },
  )
