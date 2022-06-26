package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, Selector as JSelector, SelectionKey as JSelectionKey }

import zio.Duration
import zio.nio.channels.spi.SelectorProvider
import zio.nio.core.channels.SelectionKey
import zio.{ IO, Managed, UIO }

import scala.jdk.CollectionConverters.*

class Selector(private[nio] val selector: JSelector) {

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

  final private[channels] val close: IO[IOException, Unit] =
    ZIO.attempt(selector.close()).refineToOrDie[IOException].unit
}

object Selector {

  final val make: Managed[IOException, Selector] = {
    val open = ZIO.attempt(new Selector(JSelector.open())).refineToOrDie[IOException]
    Managed.acquireReleaseWith(open)(_.close.orDie)
  }
}
