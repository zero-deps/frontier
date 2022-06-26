package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ FileChannel as JFileChannel }
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute

import zio.blocking.{ Blocking, * }
import zio.nio.core.file.Path
import zio.nio.core.{ ByteBuffer, MappedByteBuffer }
import zio.{ IO, ZIO }

import scala.collection.JavaConverters.*
import zio.ZIO.attemptBlocking

final class FileChannel private[channels] (override protected[channels] val channel: JFileChannel)
    extends GatheringByteChannel
    with ScatteringByteChannel {
  def position: IO[IOException, Long] = ZIO.attempt(channel.position()).refineToOrDie[IOException]

  def position(newPosition: Long): IO[Exception, Unit] =
    ZIO.attempt(channel.position(newPosition)).unit.refineToOrDie[Exception]

  def size: IO[IOException, Long] = ZIO.attempt(channel.size()).refineToOrDie[IOException]

  def truncate(size: Long): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(channel.truncate(size)).unit.refineToOrDie[Exception]

  def force(metadata: Boolean): ZIO[Blocking, IOException, Unit] =
    attemptBlocking(channel.force(metadata)).refineToOrDie[IOException]

  def transferTo(position: Long, count: Long, target: GatheringByteChannel): ZIO[Blocking, Exception, Long] =
    attemptBlocking(channel.transferTo(position, count, target.channel)).refineToOrDie[Exception]

  def transferFrom(src: ScatteringByteChannel, position: Long, count: Long): ZIO[Blocking, Exception, Long] =
    attemptBlocking(channel.transferFrom(src.channel, position, count)).refineToOrDie[Exception]

  def read(dst: ByteBuffer, position: Long): ZIO[Blocking, Exception, Int] =
    dst
      .withJavaBuffer[Blocking, Throwable, Int](buffer => attemptBlocking(channel.read(buffer, position)))
      .refineToOrDie[Exception]

  def write(src: ByteBuffer, position: Long): ZIO[Blocking, Exception, Int] =
    src
      .withJavaBuffer[Blocking, Throwable, Int](buffer => attemptBlocking(channel.write(buffer, position)))
      .refineToOrDie[Exception]

  def map(mode: JFileChannel.MapMode, position: Long, size: Long): ZIO[Blocking, Exception, MappedByteBuffer] =
    ZIO
      .environmentWithZIO[Blocking](_.get.effectBlocking(new MappedByteBuffer(channel.map(mode, position, size))))
      .refineToOrDie[Exception]

  def lock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): ZIO[Blocking, Exception, FileLock] =
    attemptBlocking(new FileLock(channel.lock(position, size, shared))).refineToOrDie[Exception]

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[Exception, Option[FileLock]] =
    ZIO.attempt(Option(channel.tryLock(position, size, shared)).map(new FileLock(_))).refineToOrDie[Exception]
}

object FileChannel {

  def open(
    path: Path,
    options: Set[? <: OpenOption],
    attrs: FileAttribute[?]*
  ): ZIO[Blocking, Exception, FileChannel] =
    attemptBlocking(new FileChannel(JFileChannel.open(path.javaPath, options.asJava, attrs *)))
      .refineToOrDie[Exception]

  def open(path: Path, options: OpenOption*): ZIO[Blocking, Exception, FileChannel] =
    attemptBlocking(new FileChannel(JFileChannel.open(path.javaPath, options *)))
      .refineToOrDie[Exception]

  def fromJava(javaFileChannel: JFileChannel): ZIO[Blocking, Nothing, FileChannel] =
    attemptBlocking(new FileChannel(javaFileChannel)).orDie

  type MapMode = JFileChannel.MapMode

  object MapMode {
    def READ_ONLY: FileChannel.MapMode  = JFileChannel.MapMode.READ_ONLY
    def READ_WRITE: FileChannel.MapMode = JFileChannel.MapMode.READ_WRITE
    def PRIVATE: FileChannel.MapMode    = JFileChannel.MapMode.PRIVATE
  }
}
