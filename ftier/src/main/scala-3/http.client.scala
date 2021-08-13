package ftier
package http
package client

import zio.*, blocking.*, stream.*
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.net.URI
import java.time.Duration

import ext.{*, given}

case object Timeout
type Err = Timeout.type

def send(cp: ConnectionPool, request: Request): ZIO[Blocking, Err, Response] = for {
  uri  <- IO.effect(URI(request.url)).orDie
  body <- request.body.runCollect
  reqb <- IO.effect(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(body.toArray)).nn).orDie
  _    <- if request.headers.nonEmpty then IO.effect(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else IO.unit
  req  <- IO.effect(reqb.build()).orDie
  resp <-
    (effectBlocking(cp.client.send(req, BodyHandlers.ofByteArray()).nn).map(resp =>
      Response(resp.statusCode().nn, Nil, ZStream.fromChunk(Chunk.fromArray(resp.body().nn)))
    ).catchAll(e => e.getCause.toOption match
        case Some(e1: java.net.http.HttpConnectTimeoutException) => IO.fail(Timeout)
        case Some(e1) => IO.die(e1)
        case None => IO.die(e)
    ): ZIO[Blocking, Err, Response])
} yield resp

def sendAsync(cp: ConnectionPool, request: Request): IO[Err, Response] = {
  import scala.jdk.FutureConverters.*
  for {
    uri  <- IO.effect(URI(request.url)).orDie
    body <- request.body.runCollect
    reqb <- IO.effect(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(body.toArray)).nn).orDie
    _    <- if request.headers.nonEmpty then IO.effect(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else IO.unit
    req  <- IO.effect(reqb.build()).orDie
    resp <-
      (ZIO.fromFuture(_ => cp.client.sendAsync(req, BodyHandlers.ofByteArray()).nn.asScala).map(resp =>
        Response(resp.statusCode().nn, Nil, ZStream.fromChunk(Chunk.fromArray(resp.body().nn)))
      ).catchAll(e => e.getCause.toOption match
          case Some(e1: java.net.http.HttpConnectTimeoutException) => IO.fail(Timeout)
          case Some(e1) => IO.die(e1)
          case None => IO.die(e)
      ): IO[Err, Response])
  } yield resp
}

case class ConnectionPool(client: HttpClient)

val connectionPool: UIO[ConnectionPool] = 
  IO.succeed(ConnectionPool(
    HttpClient.newBuilder().nn
      .version(HttpClient.Version.HTTP_1_1).nn
      .followRedirects(HttpClient.Redirect.NORMAL).nn
      .connectTimeout(Duration.ofSeconds(3)).nn
      .build().nn
  ))
