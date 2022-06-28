package ftier
package nio
package core.file

import java.io.IOException
import java.nio.file.{
  ClosedWatchServiceException,
  WatchEvent,
  WatchKey as JWatchKey,
  WatchService as JWatchService,
  Watchable as JWatchable,
  Path as JPath
}
import java.util.concurrent.TimeUnit


import zio.Duration

import scala.jdk.CollectionConverters.*
import zio.*

trait Watchable {
  protected def javaWatchable: JWatchable

  final def register(watcher: WatchService, events: WatchEvent.Kind[?]*): IO[Exception, WatchKey] =
    ZIO.attempt(new WatchKey(javaWatchable.register(watcher.javaWatchService, events *).nn)).refineToOrDie[Exception]

  final def register(
    watcher: WatchService,
    events: Iterable[WatchEvent.Kind[?]],
    modifiers: WatchEvent.Modifier*
  ): IO[Exception, WatchKey] =
    ZIO.attempt(
      new WatchKey(
        javaWatchable.register(
          watcher.javaWatchService: JWatchService | Null
        , events.toArray.map(x => x: WatchEvent.Kind[?] | Null): Array[WatchEvent.Kind[?] | Null] | Null
        , modifiers.map(x => x: WatchEvent.Modifier | Null) *
        ).nn
      )
    ).refineToOrDie[Exception]
}

object Watchable {

  def apply(jWatchable: JWatchable): Watchable =
    new Watchable {
      override protected val javaWatchable = jWatchable
    }
}

final class WatchKey private[file] (private val javaKey: JWatchKey) {
  def isValid: UIO[Boolean] = ZIO.succeed(javaKey.isValid)

  def pollEvents: UIO[List[WatchEvent[?]]] = ZIO.succeed(javaKey.pollEvents().nn.asScala.toList)

  def reset: UIO[Boolean] = ZIO.succeed(javaKey.reset())

  def cancel: UIO[Unit] = ZIO.succeed(javaKey.cancel())

  def watchable: UIO[Watchable] =
    ZIO.succeed(javaKey.watchable()).map {
      case javaPath: JPath => Path.fromJava(javaPath)
      case javaWatchable   => Watchable(javaWatchable.nn)
    }
}

final class WatchService private (private[file] val javaWatchService: JWatchService) {
  def close: IO[IOException, Unit] = ZIO.attempt(javaWatchService.close()).refineToOrDie[IOException]

  def poll: IO[ClosedWatchServiceException, Option[WatchKey]] =
    ZIO.attempt(javaWatchService.poll().toOption.map(new WatchKey(_))).refineToOrDie[ClosedWatchServiceException]

  def poll(timeout: Duration): IO[Exception, Option[WatchKey]] =
    ZIO.attempt(javaWatchService.poll(timeout.toNanos, TimeUnit.NANOSECONDS).toOption.map(new WatchKey(_)))
      .refineToOrDie[Exception]

  def take: IO[Exception, WatchKey] =
    ZIO.attemptBlocking(new WatchKey(javaWatchService.take().nn)).refineToOrDie[Exception]
}

object WatchService {
  def forDefaultFileSystem: IO[Exception, WatchService] = FileSystem.default.newWatchService

  def fromJava(javaWatchService: JWatchService): WatchService = new WatchService(javaWatchService)
}
