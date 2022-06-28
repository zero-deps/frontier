package zio.nio
package core.channels

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, Selector as JSelector, SelectionKey as JSelectionKey }

import zio.Duration
import zio.nio.core.channels.spi.SelectorProvider
import zio.*

import scala.jdk.CollectionConverters.*

class Selector(private[nio] val selector: JSelector) {
  final val isOpen: UIO[Boolean] = ZIO.succeed(selector.isOpen)

  final val provider: UIO[SelectorProvider] =
    ZIO.succeed(selector.provider()).map(new SelectorProvider(_))

  final val keys: IO[ClosedSelectorException, Set[SelectionKey]] =
    ZIO.attempt(selector.keys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  final val selectedKeys: IO[ClosedSelectorException, Set[SelectionKey]] =
    ZIO.attempt(selector.selectedKeys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  final def removeKey(key: SelectionKey): IO[ClosedSelectorException, Unit] =
    ZIO.attempt(selector.selectedKeys().remove(key.selectionKey))
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
    ZIO.succeed(selector.wakeup()).map(new Selector(_))

  final val close: IO[IOException, Unit] =
    ZIO.attempt(selector.close()).refineToOrDie[IOException].unit
}

object Selector {

  final val make: IO[IOException, Selector] =
    ZIO.attempt(new Selector(JSelector.open())).refineToOrDie[IOException]
}
