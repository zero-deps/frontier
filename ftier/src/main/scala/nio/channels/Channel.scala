package zio.nio.channels

import java.nio.channels.{ Channel as JChannel }

import zio.*

trait Channel {
  protected val channel: JChannel

  final private[channels] val close: IO[Exception, Unit] =
    ZIO.attempt(channel.close()).refineToOrDie[Exception]

  /**
   * Tells whether or not this channel is open.
   */
  final def isOpen: UIO[Boolean] =
    ZIO.succeed(channel.isOpen)
}
