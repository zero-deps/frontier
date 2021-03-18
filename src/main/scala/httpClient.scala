package ftier

import zio.*, blocking.*
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.net.URI
import java.time.Duration

import http.*

object httpClient {
    
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
    //     c <- tcp.connect(cp, channel => Ref.make[HttpState](HttpState()).map(state => httpProtocol(channel, state, p)), addr).mapError(Throwed.apply)
    //     _ <- tcp.write(c, build(req)).mapError(Throwed.apply)
    //     v <- p.await
    //     _ <- tcp.close(c).mapError(Throwed.apply)
    // } yield v

    def send(cp: ConnectionPool, request: Request): ZIO[Blocking, Err, Response] = for {
        uri  <- IO.effect(URI(request.url)).mapError(HttpErr.BadUri.apply)
        reqb <- IO.effect(HttpRequest.newBuilder(uri).method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.body.toArray))).mapError(Throwed.apply)
        _    <- if (request.headers.nonEmpty) IO.effect(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).mapError(Throwed.apply) else IO.unit
        req  <- IO.effect(reqb.build()).mapError(Throwed.apply)
        resp <- effectBlocking(cp.client.send(req, BodyHandlers.ofByteArray())).map(resp =>
                    Response(resp.statusCode(), Map.empty, Chunk.fromArray(resp.body()))
                ).mapError(Throwed.apply)
    } yield resp

    def sendAsync(cp: ConnectionPool, request: Request): IO[Err, Response] = { //todo: Err -> ClientErr
      import scala.jdk.FutureConverters.*

      for {
        uri  <- IO.effect(URI(request.url)).mapError(HttpErr.BadUri.apply)
        reqb <- IO.effect(HttpRequest.newBuilder(uri).method(request.method, HttpRequest.BodyPublishers.ofByteArray(request.body.toArray))).mapError(Throwed.apply)
        _    <- if (request.headers.nonEmpty) IO.effect(reqb.headers(request.headers.toList.flatMap(x => x._1 :: x._2 :: Nil) *)).mapError(Throwed.apply) else IO.unit
        req  <- IO.effect(reqb.build()).mapError(Throwed.apply)
        resp <- ZIO.fromFuture(_ => cp.client.sendAsync(req, BodyHandlers.ofByteArray()).asScala).map(resp =>
                    Response(resp.statusCode(), Map.empty, Chunk.fromArray(resp.body()))
                ).mapError(Throwed.apply)
      } yield resp
    }

    case class ConnectionPool(client: HttpClient)
    
    val connectionPool: UIO[ConnectionPool] = 
        IO.succeed(ConnectionPool(HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build()
        ))
}
