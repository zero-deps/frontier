package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ FileLock as JFileLock }

import zio.{ IO, UIO }

final class FileLock private[channels] (javaLock: JFileLock) {

  def acquiredBy: Channel =
    new Channel {
      override protected val channel = javaLock.acquiredBy
    }

  def position: Long = javaLock.position

  def size: Long = javaLock.size

  def isShared: Boolean = javaLock.isShared

  def overlaps(position: Long, size: Long): Boolean = javaLock.overlaps(position, size)

  def isValid: UIO[Boolean] = ZIO.succeed(javaLock.isValid())

  def release: IO[IOException, Unit] = ZIO.attempt(javaLock.release()).refineToOrDie[IOException]
}

object FileLock {
  def fromJava(javaLock: JFileLock): FileLock = new FileLock(javaLock)
}
