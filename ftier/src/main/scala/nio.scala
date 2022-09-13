package ftier

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.{Selector as JSelector, SocketChannel as JSocketChannel, ServerSocketChannel as JServerSocketChannel, CancelledKeyException}
import java.nio.channels.SelectionKey.{OP_ACCEPT, OP_READ}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{Files as JFiles, Paths as JPaths, FileAlreadyExistsException, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import zio.*, ZIO.*

type ByteBuffer = java.nio.ByteBuffer
type Path = java.nio.file.Path
type SelectionKey = java.nio.channels.SelectionKey
type SocketAddress = java.net.SocketAddress

type ClosedChannelException = java.nio.channels.ClosedChannelException
type IOException = java.io.IOException

object SelectionKey:
  enum Op(val code: Int):
    case Accept extends Op(OP_ACCEPT)
    case Read extends Op(OP_READ)

object ByteBuffer:
  def allocate(capacity: Int): UIO[ByteBuffer] =
    attempt(java.nio.ByteBuffer.allocate(capacity).nn).orDie

  def wrap(chunk: Chunk[Byte]): UIO[ByteBuffer] =
    succeed(java.nio.ByteBuffer.wrap(chunk.toArray).nn)

extension (byteBuffer: ByteBuffer)
  def getChunk: UIO[Chunk[Byte]] =
    for
      maxLength <- succeed(Int.MaxValue)
      rem <- succeed(byteBuffer.remaining)
      array <- succeed(Array.ofDim[Byte](math.min(maxLength, rem)))
      _ <- attempt(byteBuffer.get(array)).orDie
    yield Chunk.fromArray(array)
  
  def putChunk(chunk: Chunk[Byte]): UIO[Unit] =
    attempt(byteBuffer.put(chunk.toArray)).unit.orDie

  def getShortIO: UIO[Short] =
    attempt(byteBuffer.getShort).orDie

  def putIO(element: Byte): UIO[Unit] =
    attempt(byteBuffer.put(element)).unit.orDie

  def putShortIO(value: Short): UIO[Unit] =
    attempt(byteBuffer.putShort(value)).unit.orDie

  def putLongIO(value: Long): UIO[Unit] =
    attempt(byteBuffer.putLong(value)).unit.orDie
  
  def flipIO: UIO[Unit] =
    succeed(byteBuffer.flip).unit

  def clearIO: UIO[Unit] =
    succeed(byteBuffer.clear).unit

extension (key: SelectionKey)
  def isAcceptableIO: UIO[Boolean] =
    attempt(key.isAcceptable).orDie

  def isReadableIO: UIO[Boolean] =
    attempt(key.isReadable).orDie

  def isValidIO: UIO[Boolean] =
    attempt(key.isValid).orDie

  def attachmentIO: UIO[Option[AnyRef]] =
    succeed(key.attachment).map(_.toOption)
  
  def attachIO(ob: Option[AnyRef]): UIO[Option[AnyRef | Null]] =
    succeed(Option(key.attach(ob.orNull)))

  def attachIO(ob: AnyRef): UIO[AnyRef | Null] =
    succeed(key.attach(ob))

  def socketChannel: Task[SocketChannel] =
    succeed(key.channel.nn).flatMap(ch => attempt(ch.asInstanceOf[JSocketChannel])).flatMap(SocketChannel.fromJava)

  def serverSocketChannel: Task[ServerSocketChannel] =
    succeed(key.channel.nn).flatMap(ch => attempt(ch.asInstanceOf[JServerSocketChannel])).flatMap(ServerSocketChannel.fromJava)

class Selector(sel: JSelector):
  def select(timeout: Duration): IO[IOException, Int] =
    attempt(sel.select(timeout.toMillis)).refineToOrDie[IOException]
  
  val selectedKeys: UIO[Set[SelectionKey]] =
    attempt(sel.selectedKeys.nn).orDie
      .map(_.asScala.toSet[SelectionKey])

  def removeKey(key: SelectionKey): UIO[Unit] =
    attempt(sel.selectedKeys.nn).orDie.map(_.remove(key)).unit

  def use[A](f: JSelector => A): A =
    f(sel)

  val close: IO[IOException, Unit] =
    attempt(sel.close).unit.refineToOrDie[IOException]

object Selector:
  val make: IO[IOException, Selector] =
    attempt(Selector(JSelector.open.nn)).refineToOrDie[IOException]

class SocketChannel(ch: JSocketChannel):
  def register(sel: Selector, op: SelectionKey.Op): IO[ClosedChannelException, SelectionKey] =
    attempt(sel.use(ch.register(_, op.code).nn)).refineToOrDie[ClosedChannelException]

  def isConnected: UIO[Boolean] =
    succeed(ch.isConnected)
  
  def read(b: ByteBuffer): IO[IOException, Int] =
    attempt(ch.read(b)).refineToOrDie[IOException]

  def write(b: ByteBuffer): IO[IOException, Int] =
    attempt(ch.write(b)).refineToOrDie[IOException]

  def writeChunk(chunk: Chunk[Byte]): IO[IOException, Long] =
    for
      b <- ByteBuffer.wrap(chunk)
      n <-
        def go(buffer: ByteBuffer): IO[IOException, Long] =
          for
            n <- attempt(ch.write(buffer)).refineToOrDie[IOException]
            _ <- go(buffer).unless(!buffer.hasRemaining)
          yield n
        go(b)
    yield n

  def configureBlocking(block: Boolean): IO[IOException, Unit] =
    attempt(ch.configureBlocking(block)).unit.refineToOrDie[IOException]
  
  val socket: UIO[Socket] =
    succeed(ch.socket.nn)

  def close: IO[IOException, Unit] =
    attempt(ch.close).refineToOrDie[IOException]

object SocketChannel:
  def fromJava(ch: JSocketChannel): UIO[SocketChannel] =
    succeed(SocketChannel(ch))

class ServerSocketChannel(ch: JServerSocketChannel):
  def bind(local: SocketAddress): IO[IOException, Unit] =
    attempt(ch.bind(local)).unit.refineToOrDie[IOException]

  def register(sel: Selector, op: SelectionKey.Op): IO[ClosedChannelException, SelectionKey] =
    attempt(sel.use(ch.register(_, op.code).nn)).refineToOrDie[ClosedChannelException]

  def acceptOrFail: IO[IOException, SocketChannel] =
    attempt(ch.accept.toOption.map(SocketChannel(_))).refineToOrDie[IOException]
      .flatMap(fromOption(_).orDieWith(_ => CancelledKeyException()))

  def configureBlocking(block: Boolean): IO[IOException, Unit] =
    attempt(ch.configureBlocking(block)).unit.refineToOrDie[IOException]

  def close: IO[IOException, Unit] =
    attempt(ch.close).refineToOrDie[IOException]

object ServerSocketChannel:
  val open: IO[IOException, ServerSocketChannel] =
    attempt(ServerSocketChannel(JServerSocketChannel.open.nn)).refineToOrDie[IOException]

  def fromJava(ch: JServerSocketChannel): UIO[ServerSocketChannel] =
    succeed(ServerSocketChannel(ch))

object Paths:
  def get(path: String): UIO[Path] =
    attempt(JPaths.get(path).nn).orDie

object Files:
  def createTempFile(
    prefix: String,
    suffix: Option[String] = None,
    fileAttributes: FileAttribute[?]*,
  ): IO[IOException, Path] =
    attempt(JFiles.createTempFile(prefix, suffix.orNull, fileAttributes*).nn).refineToOrDie[IOException]

  def append(path: Path, value: Array[Byte]): IO[IOException | FileAlreadyExistsException, Unit] =
    attempt(JFiles.write(path, value, StandardOpenOption.APPEND)).unit
      .refineToOrDie[IOException | FileAlreadyExistsException]
