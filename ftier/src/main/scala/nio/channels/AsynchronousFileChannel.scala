package ftier
package nio
package channels

import ftier.nio.core.{ Buffer, ByteBuffer }
import ftier.nio.core.channels.FileLock
import ftier.nio.core.file.Path
import java.io.IOException
import java.nio.channels.{ AsynchronousFileChannel as JAsynchronousFileChannel, FileLock as JFileLock }
import java.nio.file.attribute.FileAttribute
import java.nio.file.OpenOption
import scala.concurrent.ExecutionContextExecutorService
import scala.jdk.CollectionConverters.*
import zio.*, managed.*

class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  final def force(metaData: Boolean): IO[IOException, Unit] =
    ZIO.attempt(channel.force(metaData)).refineToOrDie[IOException]

  final def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    ZIO.asyncWithCompletionHandler[JFileLock](channel.lock(position, size, shared, (), _))
      .map(FileLock.fromJava(_))
      .refineToOrDie[Exception]

  final private[nio] def readBuffer(dst: ByteBuffer, position: Long): IO[Exception, Int] =
    dst.withJavaBuffer { buf =>
      ZIO.asyncWithCompletionHandler[Integer](channel.read(buf, position, (), _))
        .map(_.intValue)
        .refineToOrDie[Exception]
    }

  final def read(capacity: Int, position: Long): IO[Exception, Chunk[Byte]] =
    for {
      b     <- Buffer.byte(capacity)
      count <- readBuffer(b, position)
      a     <- b.array
    } yield Chunk.fromArray(a).take(math.max(count, 0))

  final val size: IO[IOException, Long] =
    ZIO.attempt(channel.size()).refineToOrDie[IOException]

  final def truncate(size: Long): IO[Exception, Unit] =
    ZIO.attempt(channel.truncate(size)).refineToOrDie[Exception].unit

  final def tryLock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    ZIO.attempt(FileLock.fromJava(channel.tryLock(position, size, shared).nn)).refineToOrDie[Exception]

  final private[nio] def writeBuffer(src: ByteBuffer, position: Long): IO[Exception, Int] =
    src.withJavaBuffer { buf =>
      ZIO.asyncWithCompletionHandler[Integer](channel.write(buf, position, (), _))
        .map(_.intValue)
        .refineToOrDie[Exception]
    }

  final def write(src: Chunk[Byte], position: Long): IO[Exception, Int] =
    for {
      b <- Buffer.byte(src)
      r <- writeBuffer(b, position)
    } yield r
}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*): ZManaged[Any, Exception, AsynchronousFileChannel] = {
    val open = ZIO
      .attempt(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options *).nn))
      .refineToOrDie[Exception]

    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }

  def openWithExecutor(
    file: Path,
    options: Set[? <: OpenOption],
    executor: Option[ExecutionContextExecutorService],
    attrs: Set[FileAttribute[?]] = Set.empty
  ): ZManaged[Any, Exception, AsynchronousFileChannel] = {
    val open = ZIO
      .attempt(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options.asJava, executor.orNull, attrs.toSeq *).nn))
      .refineToOrDie[Exception]

    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }
}
