package ftier
package nio
package core.channels

import java.io.IOException
import java.nio.channels.{ Pipe as JPipe }

import ftier.nio.core.channels
import zio.*

class Pipe(private val pipe: JPipe):

  final val source: UIO[Pipe.SourceChannel] =
    ZIO.succeed(new channels.Pipe.SourceChannel(pipe.source().nn))

  final val sink: UIO[Pipe.SinkChannel] =
    ZIO.succeed(new Pipe.SinkChannel(pipe.sink().nn))

object Pipe:

  final class SinkChannel(override protected[channels] val channel: JPipe.SinkChannel)
      extends GatheringByteChannel
      with SelectableChannel

  final class SourceChannel(override protected[channels] val channel: JPipe.SourceChannel)
      extends ScatteringByteChannel
      with SelectableChannel

  final val open: IO[IOException, Pipe] =
    ZIO.attempt(new Pipe(JPipe.open().nn)).refineToOrDie[IOException]
