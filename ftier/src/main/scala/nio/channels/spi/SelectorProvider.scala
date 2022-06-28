package ftier
package nio
package channels.spi

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.channels.{ Channel as JChannel, DatagramChannel as JDatagramChannel }
import java.nio.channels.spi.{ SelectorProvider as JSelectorProvider }

import ftier.nio.channels.{ Selector, ServerSocketChannel, SocketChannel }
import ftier.nio.core.channels.{ Pipe }
import zio.*, managed.*

class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  final val openDatagramChannel: IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    ZIO.attempt(selectorProvider.openDatagramChannel().nn).refineToOrDie[IOException]

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  final def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    ZIO.attempt(selectorProvider.openDatagramChannel(family).nn).refineToOrDie[IOException]

  final val openPipe: IO[IOException, Pipe] =
    ZIO.attempt(new Pipe(selectorProvider.openPipe().nn)).refineToOrDie[IOException]

  final val openSelector: IO[IOException, Selector] =
    ZIO.attempt(new Selector(selectorProvider.openSelector().nn)).refineToOrDie[IOException]

  final val openServerSocketChannel: ZManaged[Any, IOException, ServerSocketChannel] =
    ServerSocketChannel.fromJava(selectorProvider.openServerSocketChannel().nn)

  final val openSocketChannel: IO[IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(selectorProvider.openSocketChannel().nn)).refineToOrDie[IOException]

  final val inheritedChannel: IO[IOException, Option[JChannel]] = // TODO: wrapper for Channel
    ZIO.attempt(selectorProvider.inheritedChannel().toOption).refineToOrDie[IOException]
}

object SelectorProvider {

  final val make: IO[Nothing, SelectorProvider] =
    ZIO.succeed(JSelectorProvider.provider().nn).map(new SelectorProvider(_))
}
