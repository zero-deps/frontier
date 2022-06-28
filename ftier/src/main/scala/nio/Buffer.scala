package ftier
package nio
package core

import java.nio.{
  BufferUnderflowException,
  ByteOrder,
  ReadOnlyBufferException,
  Buffer as JBuffer,
  ByteBuffer as JByteBuffer,
  CharBuffer as JCharBuffer,
  DoubleBuffer as JDoubleBuffer,
  FloatBuffer as JFloatBuffer,
  IntBuffer as JIntBuffer,
  LongBuffer as JLongBuffer,
  ShortBuffer as JShortBuffer
}

import zio.*

import scala.reflect.ClassTag

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private[nio] (private[nio] val buffer: JBuffer):
  final def capacity: Int = buffer.capacity

  def order: ByteOrder

  final def position: UIO[Int] = ZIO.succeed(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    ZIO.attempt(buffer.position(newPosition)).unit.refineToOrDie[Exception]

  final def limit: UIO[Int] = ZIO.succeed(buffer.limit)

  final def remaining: UIO[Int] = ZIO.succeed(buffer.remaining)

  final def hasRemaining: UIO[Boolean] = ZIO.succeed(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    ZIO.attempt(buffer.limit(newLimit)).unit.refineToOrDie[Exception]

  final def mark: UIO[Unit] = ZIO.succeed(buffer.mark()).unit

  final def reset: IO[Exception, Unit] =
    ZIO.attempt(buffer.reset()).unit.refineToOrDie[Exception]

  final def clear: UIO[Unit] = ZIO.succeed(buffer.clear()).unit

  final def flip: UIO[Unit] = ZIO.succeed(buffer.flip()).unit

  final def rewind: UIO[Unit] = ZIO.succeed(buffer.rewind()).unit

  final def isReadOnly: Boolean = buffer.isReadOnly

  final def hasArray: Boolean = buffer.hasArray

  final def isDirect: Boolean = buffer.isDirect

  def slice: IO[Nothing, Buffer[A]]

  def compact: IO[ReadOnlyBufferException, Unit]

  def duplicate: IO[Nothing, Buffer[A]]

  final def withArray[R, E, B](
    noArray: ZIO[R, E, B]
  )(
    hasArray: (Array[A], Int) => ZIO[R, E, B]
  ): ZIO[R, E, B] =
    if buffer.hasArray then
      for
        a      <- array.orDie
        offset <- ZIO.attempt(buffer.arrayOffset()).orDie
        result <- hasArray(a, offset)
      yield result
    else
      noArray

  protected[nio] def array: IO[Exception, Array[A]]

  def get: IO[BufferUnderflowException, A]

  def get(i: Int): IO[IndexOutOfBoundsException, A]

  def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[A]]

  def put(element: A): IO[Exception, Unit]

  def put(index: Int, element: A): IO[Exception, Unit]

  def putChunk(chunk: Chunk[A]): IO[Exception, Unit]

  /**
   * Writes as much of a chunk as possible to this buffer.
   *
   * @return The remaining elements that could not fit in this buffer, if any.
   */
  final def fillFromChunk(chunk: Chunk[A]): IO[Nothing, Chunk[A]] =
    for
      r                         <- remaining
      (putChunk, remainderChunk) = chunk.splitAt(r)
      _                         <- this.putChunk(putChunk).orDie
    yield remainderChunk

  def asReadOnlyBuffer: IO[Nothing, Buffer[A]]

object Buffer:

  def byte(capacity: Int): IO[IllegalArgumentException, ByteBuffer] =
    ZIO.attempt(JByteBuffer.allocate(capacity).nn)
      .map(new ByteBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def byte(chunk: Chunk[Byte]): IO[Nothing, ByteBuffer] =
    ZIO.succeed(JByteBuffer.wrap(chunk.toArray).nn).map(new ByteBuffer(_))

  def byteDirect(capacity: Int): IO[IllegalArgumentException, ByteBuffer] =
    ZIO.attempt(new ByteBuffer(JByteBuffer.allocateDirect(capacity).nn))
      .refineToOrDie[IllegalArgumentException]

  def byteFromJava(javaBuffer: JByteBuffer): ByteBuffer = new ByteBuffer(javaBuffer)

  def char(capacity: Int): IO[IllegalArgumentException, CharBuffer] =
    ZIO.attempt(JCharBuffer.allocate(capacity).nn)
      .map(new CharBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def char(chunk: Chunk[Char]): IO[Nothing, CharBuffer] =
    ZIO.succeed(JCharBuffer.wrap(chunk.toArray).nn).map(new CharBuffer(_))

  def char(
    charSequence: CharSequence,
    start: Int,
    end: Int
  ): IO[IndexOutOfBoundsException, CharBuffer] =
    ZIO.attempt(new CharBuffer(JCharBuffer.wrap(charSequence, start, end).nn))
      .refineToOrDie[IndexOutOfBoundsException]

  def char(charSequence: CharSequence): IO[Nothing, CharBuffer] =
    ZIO.succeed(new CharBuffer(JCharBuffer.wrap(charSequence).nn))

  def charFromJava(javaBuffer: JCharBuffer): CharBuffer = new CharBuffer(javaBuffer)

  def float(capacity: Int): IO[IllegalArgumentException, FloatBuffer] =
    ZIO.attempt(JFloatBuffer.allocate(capacity).nn)
      .map(new FloatBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def float(chunk: Chunk[Float]): IO[Nothing, FloatBuffer] =
    ZIO.succeed(JFloatBuffer.wrap(chunk.toArray).nn).map(new FloatBuffer(_))

  def floatFromJava(javaBuffer: JFloatBuffer): FloatBuffer = new FloatBuffer(javaBuffer)

  def double(capacity: Int): IO[IllegalArgumentException, DoubleBuffer] =
    ZIO.attempt(JDoubleBuffer.allocate(capacity).nn)
      .map(new DoubleBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def double(chunk: Chunk[Double]): IO[Nothing, DoubleBuffer] =
    ZIO.succeed(JDoubleBuffer.wrap(chunk.toArray).nn).map(new DoubleBuffer(_))

  def doubleFromJava(javaBuffer: JDoubleBuffer): DoubleBuffer = new DoubleBuffer(javaBuffer)

  def int(capacity: Int): IO[IllegalArgumentException, IntBuffer] =
    ZIO.attempt(JIntBuffer.allocate(capacity).nn)
      .map(new IntBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def int(chunk: Chunk[Int]): IO[Nothing, IntBuffer] =
    ZIO.succeed(JIntBuffer.wrap(chunk.toArray).nn).map(new IntBuffer(_))

  def intFromJava(javaBuffer: JIntBuffer): IntBuffer = new IntBuffer(javaBuffer)

  def long(capacity: Int): IO[IllegalArgumentException, LongBuffer] =
    ZIO.attempt(JLongBuffer.allocate(capacity).nn)
      .map(new LongBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def long(chunk: Chunk[Long]): IO[Nothing, LongBuffer] =
    ZIO.succeed(JLongBuffer.wrap(chunk.toArray).nn).map(new LongBuffer(_))

  def longFromJava(javaBuffer: JLongBuffer): LongBuffer = new LongBuffer(javaBuffer)

  def short(capacity: Int): IO[IllegalArgumentException, ShortBuffer] =
    ZIO.attempt(JShortBuffer.allocate(capacity).nn)
      .map(new ShortBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def short(chunk: Chunk[Short]): IO[Nothing, ShortBuffer] =
    ZIO.succeed(JShortBuffer.wrap(chunk.toArray).nn).map(new ShortBuffer(_))

  def shortFromJava(javaBuffer: JShortBuffer): ShortBuffer = new ShortBuffer(javaBuffer)

