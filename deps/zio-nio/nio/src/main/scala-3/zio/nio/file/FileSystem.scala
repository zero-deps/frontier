package zio.nio.file

import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{ file as jf }

import zio.blocking.Blocking
import zio.nio.core.file.{ Path, WatchService }
import zio.{ UIO, ZIO, ZManaged }

import scala.jdk.CollectionConverters.*
import zio.ZIO.attemptBlocking
import zio.managed._

final class FileSystem private (private val javaFileSystem: jf.FileSystem) {
  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider()

  private def close: ZIO[Blocking, Exception, Unit] =
    attemptBlocking(javaFileSystem.close()).refineToOrDie[Exception]

  def isOpen: UIO[Boolean] = ZIO.succeed(javaFileSystem.isOpen())

  def isReadOnly: Boolean = javaFileSystem.isReadOnly

  def getSeparator: String = javaFileSystem.getSeparator

  def getRootDirectories: List[Path] = javaFileSystem.getRootDirectories.asScala.map(Path.fromJava).toList

  def getFileStores: List[jf.FileStore] = javaFileSystem.getFileStores.asScala.toList

  def supportedFileAttributeViews: Set[String] = javaFileSystem.supportedFileAttributeViews().asScala.toSet

  def getPath(first: String, more: String*): Path = Path.fromJava(javaFileSystem.getPath(first, more *))

  def getPathMatcher(syntaxAndPattern: String): jf.PathMatcher = javaFileSystem.getPathMatcher(syntaxAndPattern)

  def getUserPrincipalLookupService: UserPrincipalLookupService = javaFileSystem.getUserPrincipalLookupService

  def newWatchService: ZIO[Blocking, Exception, WatchService] =
    attemptBlocking(WatchService.fromJava(javaFileSystem.newWatchService())).refineToOrDie[Exception]
}

object FileSystem {
  private val close: FileSystem => ZIO[Blocking, Nothing, Unit] = _.close.orDie

  def fromJava(javaFileSystem: jf.FileSystem): FileSystem = new FileSystem(javaFileSystem)

  def default: FileSystem = new FileSystem(jf.FileSystems.getDefault)

  def getFileSystem(uri: URI): ZManaged[Blocking, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.getFileSystem(uri))).refineToOrDie[Exception].toManagedWith(close)

  def newFileSystem(uri: URI, env: (String, Any)*): ZManaged[Blocking, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava)))
      .refineToOrDie[Exception]
      .toManagedWith(close)

  def newFileSystem(uri: URI, env: Map[String, ?], loader: ClassLoader): ZManaged[Blocking, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader)))
      .refineToOrDie[Exception]
      .toManagedWith(close)

  def newFileSystem(path: Path, loader: ClassLoader): ZManaged[Blocking, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader)))
      .refineToOrDie[Exception]
      .toManagedWith(close)
}
