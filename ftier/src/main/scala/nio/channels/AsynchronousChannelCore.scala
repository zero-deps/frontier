package ftier
package nio
package core.channels

import ftier.nio.core.{ Buffer, SocketAddress }
import java.io.IOException
import java.lang.{ Integer as JInteger, Long as JLong, Void as JVoid }
import java.net.SocketOption
import java.nio.{ ByteBuffer as JByteBuffer }
import java.nio.channels.{AsynchronousByteChannel as JAsynchronousByteChannel}
import java.nio.channels.{AsynchronousServerSocketChannel as JAsynchronousServerSocketChannel}
import java.nio.channels.{AsynchronousSocketChannel as JAsynchronousSocketChannel}
import java.util.concurrent.TimeUnit
import zio.*

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel):

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final private[nio] def readBuffer(b: Buffer[Byte]): IO[Exception, Int] =
    ZIO.asyncWithCompletionHandler[JInteger](h => channel.read(b.buffer.asInstanceOf[JByteBuffer], (), h))
      .map(_.toInt)
      .refineToOrDie[Exception]

  final def read(capacity: Int): IO[Exception, Chunk[Byte]] =
    for
      b <- Buffer.byte(capacity)
      l <- readBuffer(b)
      a <- b.array
      r <-
        if l == -1 then
          ZIO.fail(new IOException("Connection reset by peer"))
        else
          ZIO.succeed(Chunk.fromArray(a).take(l))
    yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final private[nio] def writeBuffer(b: Buffer[Byte]): IO[Exception, Int] =
    ZIO.asyncWithCompletionHandler[JInteger](h => channel.write(b.buffer.asInstanceOf[JByteBuffer], (), h))
      .map(_.toInt)
      .refineToOrDie[Exception]

  final def write(chunk: Chunk[Byte]): IO[Exception, Int] =
    for
      b <- Buffer.byte(chunk)
      r <- writeBuffer(b)
    yield r

  /**
   * Closes this channel.
   */
  final val close: IO[Exception, Unit] =
    ZIO.attempt(channel.close()).refineToOrDie[Exception]

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    ZIO.succeed(channel.isOpen)

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel):

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections.
   */
  final def bind(address: SocketAddress): IO[Exception, Unit] =
    ZIO.attempt(channel.bind(address.jSocketAddress)).refineToOrDie[Exception].unit

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bind(address: SocketAddress, backlog: Int): IO[Exception, Unit] =
    ZIO.attempt(channel.bind(address.jSocketAddress, backlog)).refineToOrDie[Exception].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[Exception].unit

  /**
   * Accepts a connection.
   */
  final val accept: IO[Exception, AsynchronousSocketChannel] =
    ZIO.asyncWithCompletionHandler[JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel(_))
      .refineToOrDie[Exception]

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  final def localAddress: IO[Exception, Option[SocketAddress]] =
    ZIO.attempt(channel.getLocalAddress.toOption.map(SocketAddress(_))).refineToOrDie[Exception]

  /**
   * Closes this channel.
   */
  final val close: IO[Exception, Unit] =
    ZIO.attempt(channel.close()).refineToOrDie[Exception]

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    ZIO.succeed(channel.isOpen)

object AsynchronousServerSocketChannel:

  def apply(): IO[Exception, AsynchronousServerSocketChannel] =
    ZIO.attempt(JAsynchronousServerSocketChannel.open().nn)
      .refineToOrDie[Exception]
      .map(new AsynchronousServerSocketChannel(_))

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[Exception, AsynchronousServerSocketChannel] =
    ZIO.attempt(JAsynchronousServerSocketChannel.open(channelGroup.channelGroup).nn).refineOrDie {
      case e: Exception => e
    }.map(new AsynchronousServerSocketChannel(_))

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel):

  final def bind(address: SocketAddress): IO[Exception, Unit] =
    ZIO.attempt(channel.bind(address.jSocketAddress)).refineToOrDie[Exception].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[Exception].unit

  final def shutdownInput: IO[Exception, Unit] =
    ZIO.attempt(channel.shutdownInput()).refineToOrDie[Exception].unit

  final def shutdownOutput: IO[Exception, Unit] =
    ZIO.attempt(channel.shutdownOutput()).refineToOrDie[Exception].unit

  final def remoteAddress: IO[Exception, Option[SocketAddress]] =
    ZIO.attempt(channel.getRemoteAddress.toOption.map(SocketAddress(_))).refineToOrDie[Exception]

  final def localAddress: IO[Exception, Option[SocketAddress]] =
    ZIO.attempt(channel.getLocalAddress.toOption.map(SocketAddress(_))).refineToOrDie[Exception]

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    ZIO.asyncWithCompletionHandler[JVoid](h => channel.connect(socketAddress.jSocketAddress, (), h)).unit
      .refineToOrDie[Exception]

  final private[nio] def readBuffer[A](dst: Buffer[Byte], timeout: Duration): IO[Exception, Int] =
    ZIO.asyncWithCompletionHandler[JInteger] { h =>
      channel.read(
        dst.buffer.asInstanceOf[JByteBuffer],
        timeout.fold(Long.MaxValue, _.toNanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }.map(_.toInt).refineToOrDie[Exception]

  final def read[A](capacity: Int, timeout: Duration): IO[Exception, Chunk[Byte]] =
    for
      b <- Buffer.byte(capacity)
      l <- readBuffer(b, timeout)
      a <- b.array
      r <-
        if l == -1 then
          ZIO.fail(new IOException("Connection reset by peer"))
        else
          ZIO.succeed(Chunk.fromArray(a).take(l))
    yield r

  final private[nio] def readBuffer[A](
    dsts: List[Buffer[Byte]],
    offset: Int,
    length: Int,
    timeout: Duration
  ): IO[Exception, Long] =
    ZIO.asyncWithCompletionHandler[JLong](h =>
      channel.read(
        dsts.map(_.buffer.asInstanceOf[JByteBuffer]).toArray,
        offset,
        length,
        timeout.fold(Long.MaxValue, _.toNanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    ).map(_.toLong).refineToOrDie[Exception]

  final def read[A](
    capacities: List[Int],
    offset: Int,
    length: Int,
    timeout: Duration
  ): IO[Exception, List[Chunk[Byte]]] =
    for
      bs <- ZIO.collectAll(capacities.map(Buffer.byte))
      l  <- readBuffer(bs, offset, length, timeout)
      as <- ZIO.collectAll(bs.map(_.array))
      r  <-
        if l == -1 then
          ZIO.fail(new IOException("Connection reset by peer"))
        else
          ZIO.succeed(as.map(Chunk.fromArray))
    yield r

object AsynchronousSocketChannel:

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    ZIO.attempt(JAsynchronousSocketChannel.open().nn)
      .refineToOrDie[Exception]
      .map(new AsynchronousSocketChannel(_))

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    ZIO.attempt(JAsynchronousSocketChannel.open(channelGroup.channelGroup).nn).refineOrDie {
      case e: Exception => e
    }.map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)
