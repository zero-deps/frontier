package ftier
package nio
package channels

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, Selector as JSelector, SelectionKey as JSelectionKey }

import zio.Duration
import ftier.nio.channels.spi.SelectorProvider
import ftier.nio.core.channels.SelectionKey
import zio.*, managed.*

import scala.jdk.CollectionConverters.*

class Selector(private[nio] val selector: JSelector) {

  final val provider: UIO[SelectorProvider] =
    ZIO.succeed(selector.provider().nn).map(new SelectorProvider(_))

  final val keys: IO[ClosedSelectorException, Set[SelectionKey]] =
    ZIO.attempt(selector.keys().nn)
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  final val selectedKeys: IO[ClosedSelectorException, Set[SelectionKey]] =
    ZIO.attempt(selector.selectedKeys().nn)
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  final def removeKey(key: SelectionKey): IO[ClosedSelectorException, Unit] =
    ZIO.attempt(selector.selectedKeys().nn.remove(key.selectionKey))
      .unit
      .refineToOrDie[ClosedSelectorException]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final val selectNow: IO[Exception, Int] =
    ZIO.attempt(selector.selectNow()).refineToOrDie[Exception]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final def select(timeout: Duration): IO[Exception, Int] =
    ZIO.attempt(selector.select(timeout.toMillis)).refineToOrDie[Exception]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final val select: IO[Exception, Int] =
    ZIO.attempt(selector.select()).refineToOrDie[IOException]

  final val wakeup: IO[Nothing, Selector] =
    ZIO.succeed(selector.wakeup().nn).map(new Selector(_))

  final private[channels] val close: IO[IOException, Unit] =
    ZIO.attempt(selector.close()).refineToOrDie[IOException].unit
}

object Selector {

  final val make: ZManaged[Any, IOException, Selector] = {
    val open = ZIO.attempt(new Selector(JSelector.open().nn)).refineToOrDie[IOException]
    ZManaged.acquireReleaseWith(open)(_.close.orDie)
  }
}
