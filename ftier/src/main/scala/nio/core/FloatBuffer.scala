package ftier
package nio
package core

import zio.*
import java.nio.{ BufferUnderflowException, ByteOrder, ReadOnlyBufferException, FloatBuffer as JFloatBuffer }

final class FloatBuffer(floatBuffer: JFloatBuffer) extends Buffer[Float](floatBuffer) {

  override protected[nio] def array: IO[Exception, Array[Float]] =
    ZIO.attempt(floatBuffer.array().nn).refineToOrDie[Exception]

  override def order: ByteOrder = floatBuffer.order.nn

  override def slice: IO[Nothing, FloatBuffer] =
    ZIO.succeed(floatBuffer.slice().nn).map(new FloatBuffer(_))

  override def compact: IO[ReadOnlyBufferException, Unit] =
    ZIO.attempt(floatBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  override def duplicate: IO[Nothing, FloatBuffer] =
    ZIO.succeed(new FloatBuffer(floatBuffer.duplicate().nn))

  def withJavaBuffer[R, E, A](f: JFloatBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(floatBuffer)

  override def get: IO[BufferUnderflowException, Float] =
    ZIO.attempt(floatBuffer.get()).refineToOrDie[BufferUnderflowException]

  override def get(i: Int): IO[IndexOutOfBoundsException, Float] =
    ZIO.attempt(floatBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  override def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[Float]] =
    ZIO.attempt {
      val array = Array.ofDim[Float](math.min(maxLength, floatBuffer.remaining()))
      floatBuffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]

  override def put(element: Float): IO[Exception, Unit] =
    ZIO.attempt(floatBuffer.put(element)).unit.refineToOrDie[Exception]

  override def put(index: Int, element: Float): IO[Exception, Unit] =
    ZIO.attempt(floatBuffer.put(index, element)).unit.refineToOrDie[Exception]

  override def putChunk(chunk: Chunk[Float]): IO[Exception, Unit] =
    ZIO.attempt {
      val array = chunk.toArray
      floatBuffer.put(array)
    }.unit
      .refineToOrDie[Exception]

  override def asReadOnlyBuffer: IO[Nothing, FloatBuffer] =
    ZIO.succeed(floatBuffer.asReadOnlyBuffer().nn).map(new FloatBuffer(_))
}
