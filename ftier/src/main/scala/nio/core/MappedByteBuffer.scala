package zio.nio
package core

import java.nio.{ MappedByteBuffer as JMappedByteBuffer }
import zio.*

final class MappedByteBuffer private[nio] (javaBuffer: JMappedByteBuffer) extends ByteBuffer(javaBuffer) {
  def isLoaded: IO[Nothing, Boolean] = ZIO.succeed(javaBuffer.isLoaded)

  def load: IO[Nothing, Unit] = ZIO.blocking(ZIO.succeed(javaBuffer.load()).unit)

  def force: IO[Nothing, Unit] = ZIO.blocking(ZIO.succeed(javaBuffer.force()).unit)
}
