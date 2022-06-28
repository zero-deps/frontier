package ftier
package http
package client

import ftier.ext.{*, given}
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient as JHttpClient, HttpRequest}
import java.net.URI
import java.time.Duration
import zio.*, stream.*
import zio.ZIO.attemptBlocking

case object Timeout
type Err = Timeout.type

def send(c: HttpClient, request: Request): IO[Err, Response] = for
  uri  <- ZIO.attempt(URI(request.url)).orDie
  reqb <- ZIO.attempt(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.bodyAsBytes)).nn).orDie
  _    <- if request.headers.nonEmpty then ZIO.attempt(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else ZIO.unit
  req  <- ZIO.attempt(reqb.build()).orDie
  resp <-
    (attemptBlocking(c.client.send(req, BodyHandlers.ofByteArray()).nn).map(resp =>
      Response(resp.statusCode().nn, Nil, BodyChunk(Chunk.fromArray(resp.body().nn)))
    ).catchAll(e => e.getCause.toOption match
        case Some(e1: java.net.http.HttpConnectTimeoutException) => ZIO.fail(Timeout)
        case Some(e1) => ZIO.die(e1)
        case None => ZIO.die(e)
    ): IO[Err, Response])
yield resp

def sendAsync(c: HttpClient, request: Request): IO[Err, Response] =
  import scala.jdk.FutureConverters.*
  for
    uri  <- ZIO.attempt(URI(request.url)).orDie
    reqb <- ZIO.attempt(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.bodyAsBytes)).nn).orDie
    _    <- if request.headers.nonEmpty then ZIO.attempt(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else ZIO.unit
    req  <- ZIO.attempt(reqb.build()).orDie
    resp <-
      (ZIO.fromFuture(_ => c.client.sendAsync(req, BodyHandlers.ofByteArray()).nn.asScala).map(resp =>
        Response(resp.statusCode().nn, Nil, BodyChunk(Chunk.fromArray(resp.body().nn)))
      ).catchAll(e => e.getCause.toOption match
          case Some(e1: java.net.http.HttpConnectTimeoutException) => ZIO.fail(Timeout)
          case Some(e1) => ZIO.die(e1)
          case None => ZIO.die(e)
      ): IO[Err, Response])
  yield resp

case class HttpClient(client: JHttpClient)

val httpClient: UIO[HttpClient] = 
  ZIO.succeed(HttpClient(
    JHttpClient.newBuilder().nn
      .version(JHttpClient.Version.HTTP_1_1).nn
      .followRedirects(JHttpClient.Redirect.NORMAL).nn
      .connectTimeout(Duration.ofSeconds(3)).nn
      .build().nn
  ))
