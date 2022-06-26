package zio.nio.core

import java.nio.{ MappedByteBuffer as JMappedByteBuffer }

import zio.{ IO, ZIO }


final class MappedByteBuffer private[nio] (javaBuffer: JMappedByteBuffer) extends ByteBuffer(javaBuffer) {
  def isLoaded: IO[Nothing, Boolean] = ZIO.succeed(javaBuffer.isLoaded)

  def load: ZIO[Blocking, Nothing, Unit] = ZIO.environmentWithZIO(_.get.blocking(ZIO.succeed(javaBuffer.load()).unit))

  def force: ZIO[Blocking, Nothing, Unit] = ZIO.environmentWithZIO(_.get.blocking(ZIO.succeed(javaBuffer.force()).unit))
}
