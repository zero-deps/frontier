package ftier

import zio._
import zio.nio.channels.{ Channel => _, _ }
import zio.nio.core.{ Buffer, SocketAddress }

final class Channel(val read0: Unit => IO[Err, Chunk[Byte]]) {
  val read: IO[Err, Chunk[Byte]] = read0(())
}

final class Bind
  ( val isOpen: IO[Err, Boolean]
  , val close: IO[Err, Unit]
  , val localAddress: IO[Err, SocketAddress]
  )

package object udp {
  type Udp = Has[Udp.Service]

  object Udp {
    trait Service {
      def bind(localAddr: SocketAddress)(connectionHandler: Channel => UIO[Unit]): Managed[Err, Bind]
    }

    def live(mtu: Int): ZLayer[Any, Nothing, Udp] = ZLayer.succeed{
      new Udp.Service {
        def bind(addr: SocketAddress)(connectionHandler: Channel => UIO[Unit]): Managed[Err, Bind] = {
          DatagramChannel
            .bind(Some(addr))
            .mapError(Throwed)
            .withEarlyRelease
            .onExit { _ =>
              println("shutting down server")
              ZIO.unit
            }
            .mapM {
              case (close, server) =>
                Buffer
                  .byte(mtu)
                  .flatMap(
                    buffer =>
                      server
                        .receive(buffer)
                        .mapError(Throwed)
                        .tap(_ => buffer.flip)
                        .map {
                          case Some(addr) =>
                            new Channel(
                              _ => for {
                                rem <- buffer.remaining
                                x <- buffer.getChunk(rem).mapError(Throwed)
                              } yield x
                            )
                          case None =>
                            new Channel(
                              _ => for {
                                rem <- buffer.remaining
                                x <- buffer.flip.flatMap(_ => buffer.getChunk(rem)).mapError(Throwed)
                              } yield x
                            )
                        }
                        .flatMap(
                          connectionHandler
                        )
                  )
                  .forever
                  .fork
                  .as {
                    val local = server.localAddress
                      .flatMap(opt => IO.effect(opt.get).orDie)
                      .mapError(Throwed(_))
                    new Bind(server.isOpen, close.unit, local)
                  }
            }
        }
      }
    }
  }

  def bind[R <: Udp](localAddr: SocketAddress)(connectionHandler: Channel => ZIO[R, Nothing, Unit]): ZManaged[R, Err, Bind] = {
    ZManaged.environment[R].flatMap(env => env.get[Udp.Service].bind(localAddr)(conn => connectionHandler(conn).provide(env)))
  }
}
