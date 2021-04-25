package ftier
package http
package client

import zio.*, blocking.*
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.net.URI
import java.time.Duration
import util.{*, given}
    
// type ConnectionPool = tcp.ConnectionPool
// type Connection = tcp.Connection
// type ClientRes = Promise[HttpErr, Response]

// def httpProtocol(channel: SocketChannel, state: Ref[HttpState], res: ClientRes)(closed: Boolean, chunk: Chunk[Byte]): Task[Unit] = for {
//     data <- state.get
//     _ = println(s"chunk1=${data}")
//     data <- http.processChunk(chunk, data).flatMap{
//                 case s: MsgDone => toResp(s.msg).map(resp => (HttpState(), Some(resp)))
//                 case s          => IO.succeed((s, None))
//             }.foldM(
//                 err => res.fail(err) *> IO.succeed(HttpState()),
//                 _ match {
//                     case (s, Some(resp)) => res.succeed(resp) *> IO.succeed(s)
//                     case (s, None)       => IO.succeed(s)
//                 }
//             )
//     _ = println(s"chunk2=${data}")
//     _    <- state.set(data)
// } yield ()

// def send(cp: ConnectionPool, addr: SocketAddress, req: Request): IO[Err, Response] = for {
//     p <- Promise.make[HttpErr, Response]
//     c <- tcp.connect(cp, channel => Ref.make[HttpState](HttpState()).map(state => httpProtocol(channel, state, p)), addr).orDie
//     _ <- tcp.write(c, build(req)).orDie
//     v <- p.await
//     _ <- tcp.close(c).orDie
// } yield v

case class BadUri(e: Throwable) extends Throwable(e)

def send(cp: ConnectionPool, request: Request): ZIO[Blocking, BadUri, Response] = for {
    uri  <- IO.effect(URI(request.url)).mapError(BadUri(_))
    reqb <- IO.effect(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.body.toArray)).nn).orDie
    _    <- if (request.headers.nonEmpty) IO.effect(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else IO.unit
    req  <- IO.effect(reqb.build()).orDie
    resp <- effectBlocking(cp.client.send(req, BodyHandlers.ofByteArray()).nn).map(resp =>
              Response(resp.statusCode().nn, Map.empty, Chunk.fromArray(resp.body().nn))
            ).orDie
} yield resp

def sendAsync(cp: ConnectionPool, request: Request): IO[BadUri, Response] = {
  import scala.jdk.FutureConverters.*
  for {
    uri  <- IO.effect(URI(request.url)).mapError(BadUri(_))
    reqb <- IO.effect(HttpRequest.newBuilder(uri).nn.method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.body.toArray)).nn).orDie
    _    <- if (request.headers.nonEmpty) IO.effect(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).orDie else IO.unit
    req  <- IO.effect(reqb.build()).orDie
    resp <- ZIO.fromFuture(_ => cp.client.sendAsync(req, BodyHandlers.ofByteArray()).nn.asScala).map(resp =>
                Response(resp.statusCode().nn, Map.empty, Chunk.fromArray(resp.body().nn))
            ).orDie
  } yield resp
}

case class ConnectionPool(client: HttpClient)

val connectionPool: UIO[ConnectionPool] = 
  IO.succeed(ConnectionPool(
    HttpClient.newBuilder().nn
      .version(HttpClient.Version.HTTP_1_1).nn
      .followRedirects(HttpClient.Redirect.NORMAL).nn
      .connectTimeout(Duration.ofSeconds(20)).nn
      .build().nn
  ))
