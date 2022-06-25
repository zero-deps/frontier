package ftier
package http
package client

import zio.*, stream.*
import zio.ZIO.attemptBlocking
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.net.URI
import java.time.Duration

import ext.{*, given}

case object Timeout
type Err = Timeout.type

def send(cp: ConnectionPool, request: Request): IO[Err, Response] = for {
  uri  <- ZIO.attempt(URI(request.url)).orDie
  reqb <- ZIO.attempt(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.bodyAsBytes)).nn).orDie
  _    <- if request.headers.nonEmpty then ZIO.attempt(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else ZIO.unit
  req  <- ZIO.attempt(reqb.build()).orDie
  resp <-
    (attemptBlocking(cp.client.send(req, BodyHandlers.ofByteArray()).nn).map(resp =>
      Response(resp.statusCode().nn, Nil, BodyChunk(Chunk.fromArray(resp.body().nn)))
    ).catchAll(e => e.getCause.toOption match
        case Some(e1: java.net.http.HttpConnectTimeoutException) => ZIO.fail(Timeout)
        case Some(e1) => ZIO.die(e1)
        case None => ZIO.die(e)
    ): IO[Err, Response])
} yield resp

case class ConnectionPool(client: HttpClient)

val connectionPool: UIO[ConnectionPool] = 
  ZIO.succeed(ConnectionPool(
    HttpClient.newBuilder().nn
      .version(HttpClient.Version.HTTP_1_1).nn
      .followRedirects(HttpClient.Redirect.NORMAL).nn
      .connectTimeout(Duration.ofSeconds(3)).nn
      .build().nn
  ))
