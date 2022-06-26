package zio.nio.core.channels.spi

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.channels.{ Channel as JChannel, DatagramChannel as JDatagramChannel }
import java.nio.channels.spi.{ SelectorProvider as JSelectorProvider }

import zio.nio.core.channels.{ Pipe, Selector, ServerSocketChannel, SocketChannel }
import zio.*

class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  final val openDatagramChannel: IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    ZIO.attempt(selectorProvider.openDatagramChannel()).refineToOrDie[IOException]

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  final def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    ZIO.attempt(selectorProvider.openDatagramChannel(family)).refineToOrDie[IOException]

  final val openPipe: IO[IOException, Pipe] =
    ZIO.attempt(new Pipe(selectorProvider.openPipe())).refineToOrDie[IOException]

  final val openSelector: IO[IOException, Selector] =
    ZIO.attempt(new Selector(selectorProvider.openSelector())).refineToOrDie[IOException]

  final val openServerSocketChannel: IO[IOException, ServerSocketChannel] =
    ServerSocketChannel.fromJava(selectorProvider.openServerSocketChannel())

  final val openSocketChannel: IO[IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(selectorProvider.openSocketChannel())).refineToOrDie[IOException]

  final val inheritedChannel: IO[IOException, Option[JChannel]] = // TODO: wrapper for Channel
    ZIO.attempt(Option(selectorProvider.inheritedChannel())).refineToOrDie[IOException]
}

object SelectorProvider {

  final val make: IO[Nothing, SelectorProvider] =
    ZIO.succeed(JSelectorProvider.provider()).map(new SelectorProvider(_))
}
