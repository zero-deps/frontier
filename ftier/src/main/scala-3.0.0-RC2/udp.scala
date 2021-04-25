package ftier
package udp

import zio.*, console.*
import zio.nio.*, channels.{Channel as _, *}, core.*
import util.{*, given}

type Host = String
case class ChannelRead(read: IO[NoAddr.type, Tuple2[Host, Chunk[Byte]]])
case class ChannelWrite(send: Chunk[Byte] => IO[Nothing, Unit])
object NoAddr

object ChannelWrite {
  /* Creates synchronized Connection on read and write */
  def withLock(write: Chunk[Byte] => IO[Nothing, Unit]): UIO[ChannelWrite] =
    for {
      writeLock <- Semaphore.make(1)
    } yield {
      ChannelWrite(chunk => writeLock.withPermit(write(chunk)))
    }
}

class Bind
  ( val isOpen: IO[Nothing, Boolean]
  , val close: IO[Nothing, Unit]
  , val localAddress: IO[Nothing, SocketAddress]
  )

type Udp = Has[Udp.Service]

object Udp {
  trait Service {
    def bind(localAddr: SocketAddress)(connectionHandler: ChannelRead => IO[Nothing, Unit]): Managed[Nothing, Bind]
    def connect(to: SocketAddress): Managed[Nothing, ChannelWrite]
  }

  def live(mtu: Int): ZLayer[ZEnv, Nothing, Udp] = ZLayer.fromFunction{ env =>
    new Udp.Service {
      def bind(addr: SocketAddress)(connectionHandler: ChannelRead => IO[Nothing, Unit]): Managed[Nothing, Bind] =
        DatagramChannel
          .bind(Some(addr))
          .orDie
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
                      .orDie
                      .tap(_ => buffer.flip)
                      .map {
                        case Some(addr) =>
                          ChannelRead(
                            for {
                              rem <- buffer.remaining
                              h   <- IO.require(NoAddr)(IO.succeed(addr.inetSocketAddress.map(_.hostString)))
                              x   <- buffer.getChunk(rem).orDie
                            } yield h -> x
                          )
                        case None =>
                          ChannelRead(IO.fail(NoAddr))
                      }
                      .flatMap(conn =>
                        connectionHandler(conn)
                      )
                )
                .forever
                .fork
                .as {
                  val local = server.localAddress
                    .flatMap(opt => IO.effect(opt.get).orDie)
                    .orDie
                  Bind(server.isOpen, close.unit, local)
                }
          }
          .provide(env)

      def connect(to: SocketAddress): Managed[Nothing, ChannelWrite] =
        DatagramChannel
          .connect(to)
          .mapM(
            channel =>
              ChannelWrite.withLock(
                channel.write(_).orDie.unit
              )
          )
          .orDie
    }
  }
}

def bind[R <: Udp](localAddr: SocketAddress)(connectionHandler: ChannelRead => ZIO[R, Nothing, Unit]): ZManaged[R, Nothing, Bind] = {
  ZManaged.environment[R].flatMap(env => env.get[Udp.Service].bind(localAddr)(conn => connectionHandler(conn).provide(env)))
}

def connect(to: SocketAddress): ZManaged[Udp, Nothing, ChannelWrite] =
  ZManaged.environment[Udp].flatMap(_.get.connect(to))
