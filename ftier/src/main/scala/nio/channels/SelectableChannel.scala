package zio.nio.channels

import java.io.IOException
import java.net.{ SocketOption, ServerSocket as JServerSocket, Socket as JSocket }
import java.nio.channels.{
  SelectableChannel as JSelectableChannel,
  ServerSocketChannel as JServerSocketChannel,
  SocketChannel as JSocketChannel
}
import java.nio.{ ByteBuffer as JByteBuffer }

import zio.nio.channels.spi.SelectorProvider
import zio.nio.core.{ Buffer, SocketAddress }
import zio.nio.core.channels.SelectionKey
import zio.nio.core.channels.SelectionKey.Operation
import zio.*, managed.*

trait SelectableChannel extends Channel {
  protected val channel: JSelectableChannel

  final val provider: UIO[SelectorProvider] =
    ZIO.succeed(new SelectorProvider(channel.provider()))

  final val validOps: UIO[Set[Operation]] =
    ZIO.succeed(channel.validOps())
      .map(Operation.fromInt(_))

  final val isRegistered: UIO[Boolean] =
    ZIO.succeed(channel.isRegistered())

  final def keyFor(sel: Selector): UIO[Option[SelectionKey]] =
    ZIO.succeed(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

  final def register(sel: Selector, ops: Set[Operation], att: Option[AnyRef]): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops), att.orNull)))
      .refineToOrDie[IOException]

  final def register(sel: Selector, ops: Set[Operation]): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops))))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation, att: Option[AnyRef]): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, op.intVal, att.orNull)))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, op.intVal)))
      .refineToOrDie[IOException]

  final def configureBlocking(block: Boolean): IO[IOException, Unit] =
    ZIO.attempt(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final val isBlocking: UIO[Boolean] =
    ZIO.succeed(channel.isBlocking())

  final val blockingLock: UIO[AnyRef] =
    ZIO.succeed(channel.blockingLock())
}

final class SocketChannel private[channels] (override protected[channels] val channel: JSocketChannel)
    extends SelectableChannel
    with GatheringByteChannel
    with ScatteringByteChannel {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[Exception].unit

  final val shutdownInput: IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownInput()).refineToOrDie[IOException].unit

  final val shutdownOutput: IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownOutput()).refineToOrDie[IOException].unit

  final val socket: UIO[JSocket] =
    ZIO.succeed(channel.socket())

  final val isConnected: UIO[Boolean] =
    ZIO.succeed(channel.isConnected)

  final val isConnectionPending: UIO[Boolean] =
    ZIO.succeed(channel.isConnectionPending)

  final def connect(remote: SocketAddress): IO[IOException, Boolean] =
    ZIO.attempt(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

  final val finishConnect: IO[IOException, Boolean] =
    ZIO.attempt(channel.finishConnect()).refineToOrDie[IOException]

  final val remoteAddress: IO[IOException, SocketAddress] =
    ZIO.attempt(SocketAddress(channel.getRemoteAddress())).refineToOrDie[IOException]

  final def read(b: Buffer[Byte]): IO[IOException, Int] =
    ZIO.attempt(channel.read(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final def write(b: Buffer[Byte]): IO[Exception, Int] =
    ZIO.attempt(channel.write(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final val localAddress: IO[IOException, Option[SocketAddress]] =
    ZIO.attempt(Option(channel.getLocalAddress()).map(SocketAddress(_)))
      .refineToOrDie[IOException]
}

object SocketChannel {

  final def apply(channel: JSocketChannel): ZManaged[Any, IOException, SocketChannel] = {
    val open = ZIO.attempt(new SocketChannel(channel)).refineToOrDie[IOException]
    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }

  final val open: ZManaged[Any, IOException, SocketChannel] = {
    val open = ZIO.attempt(new SocketChannel(JSocketChannel.open())).refineToOrDie[IOException]
    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }

  final def open(remote: SocketAddress): ZManaged[Any, IOException, SocketChannel] = {
    val open = ZIO
      .attempt(new SocketChannel(JSocketChannel.open(remote.jSocketAddress)))
      .refineToOrDie[IOException]
    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }

  def fromJava(javaSocketChannel: JSocketChannel): ZManaged[Any, Nothing, SocketChannel] =
    ZIO.succeed(new SocketChannel(javaSocketChannel)).toManagedWith(_.close.orDie)
}

final class ServerSocketChannel private (override protected val channel: JServerSocketChannel)
    extends SelectableChannel {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  final def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.jSocketAddress, backlog)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[Exception].unit

  final val socket: UIO[JServerSocket] =
    ZIO.succeed(channel.socket())

  /**
   * Accepts a socket connection.
   *
   * Not you must manually manage the lifecyle of the returned socket, calling `close` when you're finished with it.
   *
   * @return None if this socket is in non-blocking mode and no connection is currently available to be accepted.
   */
  final def accept: IO[IOException, Option[SocketChannel]] =
    ZIO.attempt(Option(channel.accept()).map(new SocketChannel(_))).refineToOrDie[IOException]

  final val localAddress: IO[IOException, SocketAddress] =
    ZIO.attempt(new SocketAddress(channel.getLocalAddress())).refineToOrDie[IOException]
}

object ServerSocketChannel {

  final def apply(channel: JServerSocketChannel): ZManaged[Any, IOException, ServerSocketChannel] = {
    val open = ZIO.attempt(new ServerSocketChannel(channel)).refineToOrDie[IOException]
    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }

  final val open: ZManaged[Any, IOException, ServerSocketChannel] = {
    val open = ZIO.attempt(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException]
    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }

  def fromJava(javaChannel: JServerSocketChannel): ZManaged[Any, IOException, ServerSocketChannel] =
    ZIO.succeed(new ServerSocketChannel(javaChannel)).toManagedWith(_.close.orDie)
}
