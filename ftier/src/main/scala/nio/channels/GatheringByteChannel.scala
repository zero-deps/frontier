package ftier
package nio
package channels

import java.nio.channels.{ GatheringByteChannel as JGatheringByteChannel }
import java.nio.{ ByteBuffer as JByteBuffer }

import zio.*
import ftier.nio.core.Buffer

trait GatheringByteChannel extends Channel:
  override protected[channels] val channel: JGatheringByteChannel

  final def writeBuffer(srcs: List[Buffer[Byte]]): IO[Exception, Long] =
    ZIO.attempt(channel.write(unwrap(srcs))).refineToOrDie[Exception]

  final def writeBuffer(src: Buffer[Byte]): IO[Exception, Long] = writeBuffer(List(src))

  final def write(srcs: List[Chunk[Byte]]): IO[Exception, Long] =
    for
      bs <- ZIO.collectAll(srcs.map(Buffer.byte(_)))
      r  <- writeBuffer(bs)
    yield r

  final def write(src: Chunk[Byte]): IO[Exception, Long] = write(List(src))

  private def unwrap(srcs: List[Buffer[Byte]]): Array[JByteBuffer | Null] | Null =
    srcs.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
