package ftier
package nio
package core.channels

import java.io.IOException
import java.nio.channels.{ FileChannel as JFileChannel }
import java.nio.file.attribute.FileAttribute
import java.nio.file.OpenOption
import scala.collection.JavaConverters.*
import zio.*
import ftier.nio.core.file.Path
import ftier.nio.core.{ ByteBuffer, MappedByteBuffer }
import zio.ZIO.attemptBlocking

final class FileChannel private[channels] (override protected[channels] val channel: JFileChannel)
    extends GatheringByteChannel
    with ScatteringByteChannel {
  def position: IO[IOException, Long] = ZIO.attempt(channel.position()).refineToOrDie[IOException]

  def position(newPosition: Long): IO[Exception, Unit] =
    ZIO.attempt(channel.position(newPosition)).unit.refineToOrDie[Exception]

  def size: IO[IOException, Long] = ZIO.attempt(channel.size()).refineToOrDie[IOException]

  def truncate(size: Long): IO[Exception, Unit] =
    attemptBlocking(channel.truncate(size)).unit.refineToOrDie[Exception]

  def force(metadata: Boolean): IO[IOException, Unit] =
    attemptBlocking(channel.force(metadata)).refineToOrDie[IOException]

  def transferTo(position: Long, count: Long, target: GatheringByteChannel): IO[Exception, Long] =
    attemptBlocking(channel.transferTo(position, count, target.channel)).refineToOrDie[Exception]

  def transferFrom(src: ScatteringByteChannel, position: Long, count: Long): IO[Exception, Long] =
    attemptBlocking(channel.transferFrom(src.channel, position, count)).refineToOrDie[Exception]

  def read(dst: ByteBuffer, position: Long): IO[Exception, Int] =
    dst
      .withJavaBuffer[Any, Throwable, Int](buffer => attemptBlocking(channel.read(buffer, position)))
      .refineToOrDie[Exception]

  def write(src: ByteBuffer, position: Long): IO[Exception, Int] =
    src
      .withJavaBuffer[Any, Throwable, Int](buffer => attemptBlocking(channel.write(buffer, position)))
      .refineToOrDie[Exception]

  def map(mode: JFileChannel.MapMode, position: Long, size: Long): IO[Exception, MappedByteBuffer] =
    attemptBlocking(new MappedByteBuffer(channel.map(mode, position, size).nn))
      .refineToOrDie[Exception]

  def lock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[Exception, FileLock] =
    attemptBlocking(new FileLock(channel.lock(position, size, shared).nn)).refineToOrDie[Exception]

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[Exception, Option[FileLock]] =
    ZIO.attempt(channel.tryLock(position, size, shared).toOption.map(new FileLock(_))).refineToOrDie[Exception]
}

object FileChannel {

  def open(
    path: Path,
    options: Set[? <: OpenOption],
    attrs: FileAttribute[?]*
  ): IO[Exception, FileChannel] =
    attemptBlocking(new FileChannel(JFileChannel.open(path.javaPath, options.asJava, attrs *).nn))
      .refineToOrDie[Exception]

  def open(path: Path, options: OpenOption*): IO[Exception, FileChannel] =
    attemptBlocking(new FileChannel(JFileChannel.open(path.javaPath, options *).nn))
      .refineToOrDie[Exception]

  def fromJava(javaFileChannel: JFileChannel): IO[Nothing, FileChannel] =
    attemptBlocking(new FileChannel(javaFileChannel)).orDie

  type MapMode = JFileChannel.MapMode

  object MapMode {
    def READ_ONLY: FileChannel.MapMode  = JFileChannel.MapMode.READ_ONLY.nn
    def READ_WRITE: FileChannel.MapMode = JFileChannel.MapMode.READ_WRITE.nn
    def PRIVATE: FileChannel.MapMode    = JFileChannel.MapMode.PRIVATE.nn
  }
}
