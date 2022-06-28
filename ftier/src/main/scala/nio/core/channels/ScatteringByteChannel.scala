package ftier
package nio
package core

package channels

import java.nio.channels.{ ScatteringByteChannel as JScatteringByteChannel }
import java.nio.{ ByteBuffer as JByteBuffer }

import zio.*

trait ScatteringByteChannel extends Channel:
  override protected[channels] val channel: JScatteringByteChannel

  final def readBuffer(dsts: List[Buffer[Byte]]): IO[Exception, Option[Long]] =
    ZIO.attempt(channel.read(unwrap(dsts)).eofCheck).refineToOrDie[Exception]

  final def readBuffer(dst: Buffer[Byte]): IO[Exception, Option[Long]] = readBuffer(List(dst))

  final def read(capacity: Int): IO[Exception, Chunk[Byte]] =
    for
      buffer    <- Buffer.byte(capacity)
      readCount <- readBuffer(buffer)
      _         <- buffer.flip
      chunk     <- readCount.map(count => buffer.getChunk(count.toInt)).getOrElse(ZIO.succeed(Chunk.empty))
    yield chunk

  private def unwrap(dsts: List[Buffer[Byte]]): Array[JByteBuffer | Null] | Null =
    dsts.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
