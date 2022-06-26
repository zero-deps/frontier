package zio.nio.file

import java.io.{ IOException, InputStream }
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.attribute.*
import java.nio.file.{
  CopyOption,
  DirectoryStream,
  FileStore,
  FileVisitOption,
  LinkOption,
  OpenOption,
  Files as JFiles,
  Path as JPath
}
import java.util.function.BiPredicate
import java.util.{ Iterator as JIterator }


import zio.nio.core.file.Path
import zio.stream.ZStream
import zio.{ Chunk, UIO, ZIO, ZManaged }

import scala.jdk.CollectionConverters.*
import scala.reflect.*
import zio.ZIO.attemptBlocking
import zio.managed._

object Files {

  def fromJavaIterator[A](iterator: JIterator[A]): ZStream[Blocking, RuntimeException, A] =
    ZStream.unfoldZIO(()) { _ =>
      attemptBlocking {
        if (iterator.hasNext) Some((iterator.next(), ())) else None
      }.refineToOrDie[RuntimeException]
    }

  def newDirectoryStream(dir: Path, glob: String = "*"): ZStream[Blocking, Exception, Path] = {
    val managed = ZManaged.fromAutoCloseable(
      attemptBlocking(JFiles.newDirectoryStream(dir.javaPath, glob)).refineToOrDie[Exception]
    )
    ZStream.managed(managed).mapZIO(dirStream => ZIO.succeed(dirStream.iterator())).flatMap(fromJavaIterator).map(Path.fromJava)
  }

  def newDirectoryStream(dir: Path, filter: Path => Boolean): ZStream[Blocking, Exception, Path] = {
    val javaFilter: DirectoryStream.Filter[? >: JPath] = javaPath => filter(Path.fromJava(javaPath))
    val managed                                        = ZManaged.fromAutoCloseable(
      attemptBlocking(JFiles.newDirectoryStream(dir.javaPath, javaFilter)).refineToOrDie[Exception]
    )
    ZStream.managed(managed).mapZIO(dirStream => ZIO.succeed(dirStream.iterator())).flatMap(fromJavaIterator).map(Path.fromJava)
  }

  def createFile(path: Path, attrs: FileAttribute[?]*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.createFile(path.javaPath, attrs *)).unit.refineToOrDie[Exception]

  def createDirectory(path: Path, attrs: FileAttribute[?]*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.createDirectory(path.javaPath, attrs *)).unit.refineToOrDie[Exception]

  def createDirectories(path: Path, attrs: FileAttribute[?]*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.createDirectories(path.javaPath, attrs *)).unit.refineToOrDie[Exception]

  def createTempFileIn(
    dir: Path,
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[?]]
  ): ZIO[Blocking, Exception, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempFile(dir.javaPath, prefix.orNull, suffix, fileAttributes.toSeq *)))
      .refineToOrDie[Exception]

  def createTempFile(
    prefix: String,
    suffix: Option[String] = None,
    fileAttributes: FileAttribute[?]*
  ): ZIO[Blocking, Exception, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempFile(prefix, suffix.orNull, fileAttributes*)))
      .refineToOrDie[Exception]

  def createTempDirectory(
    dir: Path,
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[?]]
  ): ZIO[Blocking, Exception, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempDirectory(dir.javaPath, prefix.orNull, fileAttributes.toSeq *)))
      .refineToOrDie[Exception]

  def createTempDirectory(
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[?]]
  ): ZIO[Blocking, Exception, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempDirectory(prefix.orNull, fileAttributes.toSeq *)))
      .refineToOrDie[Exception]

  def createSymbolicLink(link: Path, target: Path, fileAttributes: FileAttribute[?]*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.createSymbolicLink(link.javaPath, target.javaPath, fileAttributes *)).unit
      .refineToOrDie[Exception]

  def createLink(link: Path, existing: Path): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.createLink(link.javaPath, existing.javaPath)).unit.refineToOrDie[Exception]

  def delete(path: Path): ZIO[Blocking, IOException, Unit] =
    attemptBlocking(JFiles.delete(path.javaPath)).refineToOrDie[IOException]

  def deleteIfExists(path: Path): ZIO[Blocking, IOException, Boolean] =
    attemptBlocking(JFiles.deleteIfExists(path.javaPath)).refineToOrDie[IOException]

  def copy(source: Path, target: Path, copyOptions: CopyOption*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.copy(source.javaPath, target.javaPath, copyOptions *)).unit
      .refineToOrDie[Exception]

  def move(source: Path, target: Path, copyOptions: CopyOption*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.move(source.javaPath, target.javaPath, copyOptions *)).unit.refineToOrDie[Exception]

  def readSymbolicLink(link: Path): ZIO[Blocking, Exception, Path] =
    attemptBlocking(Path.fromJava(JFiles.readSymbolicLink(link.javaPath))).refineToOrDie[Exception]

  def getFileStore(path: Path): ZIO[Blocking, IOException, FileStore] =
    attemptBlocking(JFiles.getFileStore(path.javaPath)).refineToOrDie[IOException]

  def isSameFile(path: Path, path2: Path): ZIO[Blocking, IOException, Boolean] =
    attemptBlocking(JFiles.isSameFile(path.javaPath, path2.javaPath)).refineToOrDie[IOException]

  def isHidden(path: Path): ZIO[Blocking, IOException, Boolean] =
    attemptBlocking(JFiles.isHidden(path.javaPath)).refineToOrDie[IOException]

  def probeContentType(path: Path): ZIO[Blocking, IOException, String] =
    attemptBlocking(JFiles.probeContentType(path.javaPath)).refineToOrDie[IOException]

  def useFileAttributeView[A <: FileAttributeView: ClassTag, B](path: Path, linkOptions: LinkOption*)(
    f: A => ZIO[Blocking, Exception, B]
  ): ZIO[Blocking, Exception, B] = {
    val viewClass =
      classTag[A].runtimeClass.asInstanceOf[Class[A]] // safe? because we know A is a subtype of FileAttributeView
    attemptBlocking(JFiles.getFileAttributeView[A](path.javaPath, viewClass, linkOptions *)).orDie
      .flatMap(f)
  }

  def readAttributes[A <: BasicFileAttributes: ClassTag](
    path: Path,
    linkOptions: LinkOption*
  ): ZIO[Blocking, Exception, A] = {
    // safe? because we know A is a subtype of BasicFileAttributes
    val attributeClass = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    attemptBlocking(JFiles.readAttributes(path.javaPath, attributeClass, linkOptions *))
      .refineToOrDie[Exception]
  }

  final case class Attribute(attributeName: String, viewName: String = "basic") {
    def toJava: String = s"$viewName:$attributeName"
  }

  object Attribute {

    def fromJava(javaAttribute: String): Option[Attribute] =
      javaAttribute.split(':').toList match {
        case name :: Nil         => Some(Attribute(name))
        case view :: name :: Nil => Some(Attribute(name, view))
        case _                   => None
      }
  }

  def setAttribute(
    path: Path,
    attribute: Attribute,
    value: Object,
    linkOptions: LinkOption*
  ): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.setAttribute(path.javaPath, attribute.toJava, value, linkOptions *)).unit
      .refineToOrDie[Exception]

  def getAttribute(path: Path, attribute: Attribute, linkOptions: LinkOption*): ZIO[Blocking, Exception, Object] =
    attemptBlocking(JFiles.getAttribute(path.javaPath, attribute.toJava, linkOptions *)).refineToOrDie[Exception]

  sealed trait AttributeNames {

    def toJava: String =
      this match {
        case AttributeNames.All         => "*"
        case AttributeNames.List(names) => names.mkString(",")
      }
  }

  object AttributeNames {
    final case class List(names: scala.List[String]) extends AttributeNames

    case object All extends AttributeNames

    def fromJava(javaNames: String): AttributeNames =
      javaNames.trim match {
        case "*"  => All
        case list => List(list.split(',').toList)
      }
  }

  final case class Attributes(attributeNames: AttributeNames, viewName: String = "base") {
    def toJava: String = s"$viewName:${attributeNames.toJava}"
  }

  object Attributes {

    def fromJava(javaAttributes: String): Option[Attributes] =
      javaAttributes.split(':').toList match {
        case names :: Nil         => Some(Attributes(AttributeNames.fromJava(names)))
        case view :: names :: Nil => Some(Attributes(AttributeNames.fromJava(names), view))
        case _                    => None
      }
  }

  def readAttributes(
    path: Path,
    attributes: Attributes,
    linkOptions: LinkOption*
  ): ZIO[Blocking, Exception, Map[String, AnyRef]] =
    attemptBlocking(JFiles.readAttributes(path.javaPath, attributes.toJava, linkOptions *))
      .map(_.asScala.toMap)
      .refineToOrDie[Exception]

  def getPosixFilePermissions(
    path: Path,
    linkOptions: LinkOption*
  ): ZIO[Blocking, Exception, Set[PosixFilePermission]] =
    attemptBlocking(JFiles.getPosixFilePermissions(path.javaPath, linkOptions *))
      .map(_.asScala.toSet)
      .refineToOrDie[Exception]

  def setPosixFilePermissions(path: Path, permissions: Set[PosixFilePermission]): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.setPosixFilePermissions(path.javaPath, permissions.asJava)).unit
      .refineToOrDie[Exception]

  def getOwner(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Exception, UserPrincipal] =
    attemptBlocking(JFiles.getOwner(path.javaPath, linkOptions *)).refineToOrDie[Exception]

  def setOwner(path: Path, owner: UserPrincipal): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.setOwner(path.javaPath, owner)).unit.refineToOrDie[Exception]

  def isSymbolicLink(path: Path): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.isSymbolicLink(path.javaPath)).orDie

  def isDirectory(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.isDirectory(path.javaPath, linkOptions *)).orDie

  def isRegularFile(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.isRegularFile(path.javaPath, linkOptions *)).orDie

  def getLastModifiedTime(path: Path, linkOptions: LinkOption*): ZIO[Blocking, IOException, FileTime] =
    attemptBlocking(JFiles.getLastModifiedTime(path.javaPath, linkOptions *)).refineToOrDie[IOException]

  def setLastModifiedTime(path: Path, time: FileTime): ZIO[Blocking, IOException, Unit] =
    attemptBlocking(JFiles.setLastModifiedTime(path.javaPath, time)).unit.refineToOrDie[IOException]

  def size(path: Path): ZIO[Blocking, IOException, Long] =
    attemptBlocking(JFiles.size(path.javaPath)).refineToOrDie[IOException]

  def exists(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.exists(path.javaPath, linkOptions *)).orDie

  def notExists(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.notExists(path.javaPath, linkOptions *)).orDie

  def isReadable(path: Path): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.isReadable(path.javaPath)).orDie

  def isWritable(path: Path): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.isWritable(path.javaPath)).orDie

  def isExecutable(path: Path): ZIO[Blocking, Nothing, Boolean] =
    attemptBlocking(JFiles.isExecutable(path.javaPath)).orDie

  def readAllBytes(path: Path): ZIO[Blocking, IOException, Chunk[Byte]] =
    attemptBlocking(Chunk.fromArray(JFiles.readAllBytes(path.javaPath))).refineToOrDie[IOException]

  def readAllLines(path: Path, charset: Charset = StandardCharsets.UTF_8): ZIO[Blocking, IOException, List[String]] =
    attemptBlocking(JFiles.readAllLines(path.javaPath, charset).asScala.toList).refineToOrDie[IOException]

  def writeBytes(path: Path, bytes: Chunk[Byte], openOptions: OpenOption*): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.write(path.javaPath, bytes.toArray, openOptions *)).unit.refineToOrDie[Exception]

  def writeLines(
    path: Path,
    lines: Iterable[CharSequence],
    charset: Charset = StandardCharsets.UTF_8,
    openOptions: Set[OpenOption] = Set.empty
  ): ZIO[Blocking, Exception, Unit] =
    attemptBlocking(JFiles.write(path.javaPath, lines.asJava, charset, openOptions.toSeq *)).unit
      .refineToOrDie[Exception]

  def list(path: Path): ZStream[Blocking, Exception, Path] =
    ZStream
      .fromJavaIteratorManaged(
        ZManaged
          .acquireReleaseWith(attemptBlocking(JFiles.list(path.javaPath)))(stream => ZIO.succeed(stream.close()))
          .map(_.iterator())
      )
      .map(Path.fromJava)
      .refineOrDie {
        case io: IOException => io
      }

  def walk(
    path: Path,
    maxDepth: Int = Int.MaxValue,
    visitOptions: Set[FileVisitOption] = Set.empty
  ): ZStream[Blocking, Exception, Path] =
    ZStream
      .fromZIO(
        attemptBlocking(JFiles.walk(path.javaPath, maxDepth, visitOptions.toSeq *).iterator())
          .refineToOrDie[IOException]
      )
      .flatMap(fromJavaIterator)
      .map(Path.fromJava)

  def find(path: Path, maxDepth: Int = Int.MaxValue, visitOptions: Set[FileVisitOption] = Set.empty)(
    test: (Path, BasicFileAttributes) => Boolean
  ): ZStream[Blocking, Exception, Path] = {
    val matcher: BiPredicate[JPath, BasicFileAttributes] = (path, attr) => test(Path.fromJava(path), attr)
    ZStream
      .fromZIO(
        attemptBlocking(JFiles.find(path.javaPath, maxDepth, matcher, visitOptions.toSeq *).iterator())
          .refineToOrDie[IOException]
      )
      .flatMap(fromJavaIterator)
      .map(Path.fromJava)
  }

  def toInputStream(p: Path): ZManaged[Blocking, Throwable, InputStream] =
    zio.stream.ZStream.fromFile(p.javaPath).toInputStream

//
//  def copy(in: ZStream[Blocking, Exception, Chunk[Byte]], target: Path, options: CopyOption*): ZIO[Blocking, Exception, Long] = {
//
//    FileChannel.open(target).flatMap { channel =>
//      in.fold[Blocking, Exception, Chunk[Byte], Long].flatMap { startFold =>
//        val f = (count: Long, chunk: Chunk[Byte]) => {
//          channel.write(chunk).map(_ + count)
//        }
//        startFold(0L, Function.const(true), f)
//      }
//    }.use(ZIO.succeed)
//  }
}
