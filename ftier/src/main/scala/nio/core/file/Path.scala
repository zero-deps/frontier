package zio.nio
package core.file

import java.io.{ File, IOError, IOException }
import java.net.URI
import java.nio.file.{ LinkOption, Paths, Path as JPath, Watchable as JWatchable }
import scala.jdk.CollectionConverters.*
import zio.*

final class Path private (private[nio] val javaPath: JPath) extends Watchable {
  import Path.*

  def filesystem: FileSystem = FileSystem.fromJava(javaPath.getFileSystem)

  def isAbsolute: Boolean = javaPath.isAbsolute

  def root: Option[Path] = Option(javaPath.getRoot).map(fromJava)

  def filename: Path = new Path(javaPath.getFileName)

  def parent: Option[Path] = Option(javaPath.getParent).map(fromJava)

  def nameCount: Int = javaPath.getNameCount

  def apply(index: Int): Path = fromJava(javaPath.getName(index))

  def subpath(beginIndex: Int, endIndex: Int): Path = fromJava(javaPath.subpath(beginIndex, endIndex))

  def startsWith(other: Path): Boolean = javaPath.startsWith(other.javaPath)

  def endsWith(other: Path): Boolean = javaPath.endsWith(other.javaPath)

  def normalize: Path = fromJava(javaPath.normalize)

  def / (other: Path): Path = fromJava(javaPath.resolve(other.javaPath))

  def / (other: String): Path = fromJava(javaPath.resolve(other))

  def resolveSibling(other: Path): Path = fromJava(javaPath.resolveSibling(other.javaPath))

  def relativize(other: Path): Path = fromJava(javaPath.relativize(other.javaPath))

  def toUri: IO[IOError, URI] =
    ZIO.attemptBlocking(javaPath.toUri).refineToOrDie[IOError]

  def toAbsolutePath: IO[IOError, Path] =
    ZIO.attemptBlocking(fromJava(javaPath.toAbsolutePath))
      .refineToOrDie[IOError]

  def toRealPath(linkOptions: LinkOption*): IO[IOException, Path] =
    ZIO.attemptBlocking(fromJava(javaPath.toRealPath(linkOptions *)))
      .refineToOrDie[IOException]

  def toFile: File = javaPath.toFile

  def elements: List[Path] = javaPath.iterator().asScala.map(fromJava).toList

  override protected def javaWatchable: JWatchable = javaPath

  override def hashCode: Int = javaPath.hashCode

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Path => this.javaPath.equals(other.javaPath)
      case _           => false
    }

  override def toString: String = javaPath.toString
}

object Path {
  def apply(first: String, more: String*): Path = new Path(Paths.get(first, more *))

  def apply(uri: URI): Path = new Path(Paths.get(uri))

  def fromJava(javaPath: JPath): Path = new Path(javaPath)
}
