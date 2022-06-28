package ftier
package nio.file

import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{ file as jf }

import ftier.nio.core.file.{ Path, WatchService }
import zio.*

import scala.jdk.CollectionConverters.*
import zio.ZIO.attemptBlocking
import zio.managed.*

final class FileSystem private (private val javaFileSystem: jf.FileSystem) {
  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider().nn

  private def close: IO[Exception, Unit] =
    attemptBlocking(javaFileSystem.close()).refineToOrDie[Exception]

  def isOpen: UIO[Boolean] = ZIO.succeed(javaFileSystem.isOpen())

  def isReadOnly: Boolean = javaFileSystem.isReadOnly

  def getSeparator: String = javaFileSystem.getSeparator.nn

  def getRootDirectories: List[Path] = javaFileSystem.getRootDirectories.nn.asScala.map(Path.fromJava).toList

  def getFileStores: List[jf.FileStore] = javaFileSystem.getFileStores.nn.asScala.toList

  def supportedFileAttributeViews: Set[String] = javaFileSystem.supportedFileAttributeViews().nn.asScala.toSet

  def getPath(first: String, more: String*): Path = Path.fromJava(javaFileSystem.getPath(first, more *).nn)

  def getPathMatcher(syntaxAndPattern: String): jf.PathMatcher = javaFileSystem.getPathMatcher(syntaxAndPattern).nn

  def getUserPrincipalLookupService: UserPrincipalLookupService = javaFileSystem.getUserPrincipalLookupService.nn

  def newWatchService: IO[Exception, WatchService] =
    attemptBlocking(WatchService.fromJava(javaFileSystem.newWatchService().nn)).refineToOrDie[Exception]
}

object FileSystem {
  private val close: FileSystem => IO[Nothing, Unit] = _.close.orDie

  def fromJava(javaFileSystem: jf.FileSystem): FileSystem = new FileSystem(javaFileSystem)

  def default: FileSystem = new FileSystem(jf.FileSystems.getDefault.nn)

  def getFileSystem(uri: URI): ZManaged[Any, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.getFileSystem(uri).nn)).refineToOrDie[Exception].toManagedWith(close)

  def newFileSystem(uri: URI, env: (String, Any)*): ZManaged[Any, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava).nn))
      .refineToOrDie[Exception]
      .toManagedWith(close)

  def newFileSystem(uri: URI, env: Map[String, ?], loader: ClassLoader): ZManaged[Any, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader).nn))
      .refineToOrDie[Exception]
      .toManagedWith(close)

  def newFileSystem(path: Path, loader: ClassLoader): ZManaged[Any, Exception, FileSystem] =
    attemptBlocking(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader).nn))
      .refineToOrDie[Exception]
      .toManagedWith(close)
}
