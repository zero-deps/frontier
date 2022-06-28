package zio.nio
package core.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousChannelGroup as JAsynchronousChannelGroup }
import java.nio.channels.spi.{ AsynchronousChannelProvider as JAsynchronousChannelProvider }
import java.util.concurrent.{ ThreadFactory as JThreadFactory }
import java.util.concurrent.TimeUnit

import zio.*


import scala.concurrent.ExecutionContextExecutorService
import zio._

object AsynchronousChannelGroup {

  def apply(executor: ExecutionContextExecutorService, initialSize: Int): IO[Exception, AsynchronousChannelGroup] =
    ZIO.attempt(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
      )
    ).refineToOrDie[Exception]

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  ): IO[Exception, AsynchronousChannelGroup] =
    ZIO.attempt(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
      )
    ).refineToOrDie[Exception]

  def apply(executor: ExecutionContextExecutorService): IO[Exception, AsynchronousChannelGroup] =
    ZIO.attempt(
      new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
    ).refineToOrDie[Exception]
}

class AsynchronousChannelGroup(val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[Exception, Boolean] =
    ZIO.attempt(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineToOrDie[Exception]

  val isShutdown: UIO[Boolean] = ZIO.succeed(channelGroup.isShutdown)

  val isTerminated: UIO[Boolean] = ZIO.succeed(channelGroup.isTerminated)

  val provider: UIO[JAsynchronousChannelProvider] = ZIO.succeed(channelGroup.provider())

  val shutdown: UIO[Unit] = ZIO.succeed(channelGroup.shutdown())

  val shutdownNow: IO[IOException, Unit] =
    ZIO.attempt(channelGroup.shutdownNow()).refineToOrDie[IOException]
}
