package ftier

import zio._, console._
import zio.nio._, channels.{Channel=>_, _}, core._

final case class ChannelRead(read: IO[Err, Tuple2[/*host:*/String,Chunk[Byte]]])
final case class ChannelWrite(send: Chunk[Byte] => IO[Err, Unit])

object ChannelWrite {
  /* Creates synchronized Connection on read and write */
  def withLock(write: Chunk[Byte] => IO[Err, Unit]): UIO[ChannelWrite] =
    for {
      writeLock <- Semaphore.make(1)
    } yield {
      ChannelWrite(chunk => writeLock.withPermit(write(chunk)))
    }
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
      def bind(localAddr: SocketAddress)(connectionHandler: ChannelRead => IO[Err, Unit]): Managed[Err, Bind]
      def connect(to: SocketAddress): Managed[Err, ChannelWrite]
    }

    def live(mtu: Int): ZLayer[ZEnv, Nothing, Udp] = ZLayer.fromFunction{ env =>
      new Udp.Service {
        def bind(addr: SocketAddress)(connectionHandler: ChannelRead => IO[Err, Unit]): Managed[Err, Bind] =
          DatagramChannel
            .bind(Some(addr))
            .mapError(Throwed.apply)
            .withEarlyRelease
            .onExit(x => putStrLn(s"shutting down server=$x"))
            .mapM {
              case (close, server) =>
                Buffer
                  .byte(mtu)
                  .flatMap(
                    buffer =>
                      server
                        .receive(buffer)
                        .mapError(Throwed.apply)
                        .tap(_ => buffer.flip)
                        .map {
                          case Some(addr) =>
                            ChannelRead(
                              for {
                                rem <- buffer.remaining
                                h   <- IO.require(UdpErr.NoAddr)(IO.succeed(addr.inetSocketAddress.map(_.hostString)))
                                x   <- buffer.getChunk(rem).mapError(Throwed.apply)
                              } yield h -> x
                            )
                          case None =>
                            ChannelRead(IO.fail(UdpErr.NoAddr))
                        }
                        .flatMap(conn =>
                          connectionHandler(conn).catchAll(ex => putStrLn(ex.toString))
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
            .provide(env)

        def connect(to: SocketAddress): Managed[Err, ChannelWrite] =
          DatagramChannel
            .connect(to)
            .mapM(
              channel =>
                ChannelWrite.withLock(
                  channel.write(_).mapError(Throwed.apply).unit
                )
            )
            .mapError(Throwed.apply)
      }
    }
  }

  def bind[R <: Udp](localAddr: SocketAddress)(connectionHandler: ChannelRead => ZIO[R, Err, Unit]): ZManaged[R, Err, Bind] = {
    ZManaged.environment[R].flatMap(env => env.get[Udp.Service].bind(localAddr)(conn => connectionHandler(conn).provide(env)))
  }

  def connect(to: SocketAddress): ZManaged[Udp, Err, ChannelWrite] =
    ZManaged.environment[Udp].flatMap(_.get.connect(to))
}

given CanEqual[None.type, Option[SocketAddress]] = CanEqual.derived
