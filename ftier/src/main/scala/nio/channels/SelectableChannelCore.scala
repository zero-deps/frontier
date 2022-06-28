package ftier
package nio
package core.channels

import java.io.IOException
import java.net.{ SocketOption, ServerSocket as JServerSocket, Socket as JSocket }
import java.nio.channels.{
  SelectableChannel as JSelectableChannel,
  ServerSocketChannel as JServerSocketChannel,
  SocketChannel as JSocketChannel
}
import java.nio.{ ByteBuffer as JByteBuffer }

import ftier.nio.core.channels.SelectionKey.Operation
import ftier.nio.core.channels.spi.SelectorProvider
import ftier.nio.core.{ Buffer, SocketAddress }
import zio.*

trait SelectableChannel extends Channel:
  protected val channel: JSelectableChannel

  final val provider: UIO[SelectorProvider] =
    ZIO.succeed(new SelectorProvider(channel.provider().nn))

  final val validOps: UIO[Set[Operation]] =
    ZIO.succeed(channel.validOps())
      .map(Operation.fromInt(_))

  final val isRegistered: UIO[Boolean] =
    ZIO.succeed(channel.isRegistered())

  final def keyFor(sel: Selector): UIO[Option[SelectionKey]] =
    ZIO.succeed(channel.keyFor(sel.selector).toOption.map(new SelectionKey(_)))

  final def register(sel: Selector, ops: Set[Operation], att: Option[AnyRef]): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops), att.orNull).nn))
      .refineToOrDie[IOException]

  final def register(sel: Selector, ops: Set[Operation]): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops)).nn))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation, att: Option[AnyRef]): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, op.intVal, att.orNull).nn))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation): IO[IOException, SelectionKey] =
    ZIO.attempt(new SelectionKey(channel.register(sel.selector, op.intVal).nn))
      .refineToOrDie[IOException]

  final def configureBlocking(block: Boolean): IO[IOException, Unit] =
    ZIO.attempt(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final val isBlocking: UIO[Boolean] =
    ZIO.succeed(channel.isBlocking())

  final val blockingLock: UIO[AnyRef | Null] =
    ZIO.succeed(channel.blockingLock())

final class SocketChannel(override protected[channels] val channel: JSocketChannel)
    extends SelectableChannel
    with GatheringByteChannel
    with ScatteringByteChannel:

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[Exception].unit

  final val shutdownInput: IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownInput()).refineToOrDie[IOException].unit

  final val shutdownOutput: IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownOutput()).refineToOrDie[IOException].unit

  final val socket: UIO[JSocket] =
    ZIO.succeed(channel.socket().nn)

  final val isConnected: UIO[Boolean] =
    ZIO.succeed(channel.isConnected)

  final val isConnectionPending: UIO[Boolean] =
    ZIO.succeed(channel.isConnectionPending)

  final def connect(remote: SocketAddress): IO[IOException, Boolean] =
    ZIO.attempt(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

  final val finishConnect: IO[IOException, Boolean] =
    ZIO.attempt(channel.finishConnect()).refineToOrDie[IOException]

  final val remoteAddress: IO[IOException, SocketAddress] =
    ZIO.attempt(SocketAddress(channel.getRemoteAddress().nn)).refineToOrDie[IOException]

  final def read(b: Buffer[Byte]): IO[IOException, Int] =
    ZIO.attempt(channel.read(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final def write(b: Buffer[Byte]): IO[Exception, Int] =
    ZIO.attempt(channel.write(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final val localAddress: IO[IOException, Option[SocketAddress]] =
    ZIO.attempt(channel.getLocalAddress().toOption.map(SocketAddress(_)))
      .refineToOrDie[IOException]

object SocketChannel:

  final def fromJava(javaSocketChannel: JSocketChannel): IO[IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(javaSocketChannel)).refineToOrDie[IOException]

  final val open: IO[IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(JSocketChannel.open().nn)).refineToOrDie[IOException]

  final def open(remote: SocketAddress): IO[IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(JSocketChannel.open(remote.jSocketAddress).nn)).refineToOrDie[IOException]

final class ServerSocketChannel(override protected val channel: JServerSocketChannel) extends SelectableChannel:

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  final def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.jSocketAddress, backlog)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[Exception].unit

  final val socket: UIO[JServerSocket] =
    ZIO.succeed(channel.socket().nn)

  /**
   * Accepts a socket connection.
   *
   * Not you must manually manage the lifecyle of the returned socket, calling `close` when you're finished with it.
   *
   * @return None if this socket is in non-blocking mode and no connection is currently available to be accepted.
   */
  final def accept: IO[IOException, Option[SocketChannel]] =
    ZIO.attempt(channel.accept().toOption.map(new SocketChannel(_))).refineToOrDie[IOException]

  final val localAddress: IO[IOException, SocketAddress] =
    ZIO.attempt(new SocketAddress(channel.getLocalAddress().nn)).refineToOrDie[IOException]

object ServerSocketChannel:

  final val open: IO[IOException, ServerSocketChannel] =
    ZIO.attempt(new ServerSocketChannel(JServerSocketChannel.open().nn)).refineToOrDie[IOException]

  def fromJava(javaChannel: JServerSocketChannel): IO[IOException, ServerSocketChannel] =
    ZIO.attempt(new ServerSocketChannel(javaChannel)).refineToOrDie[IOException]
