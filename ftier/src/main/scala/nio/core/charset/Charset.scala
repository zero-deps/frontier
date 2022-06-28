package ftier
package nio
package core
package charset

import java.{ util as ju }
import java.nio.{ charset as j }
import scala.collection.JavaConverters.*
import zio.*

final class Charset private (val javaCharset: j.Charset) extends Ordered[Charset]:

  def aliases: Set[String] = javaCharset.aliases().nn.asScala.toSet

  def canEncode: Boolean = javaCharset.canEncode

  override def compare(that: Charset): Int =
    javaCharset.compareTo(that.javaCharset)

  def contains(cs: Charset): Boolean = javaCharset.contains(cs.javaCharset)

  def decode(byteBuffer: ByteBuffer): UIO[CharBuffer] =
    byteBuffer.withJavaBuffer(jBuf => ZIO.succeed(Buffer.charFromJava(javaCharset.decode(jBuf).nn)))

  def displayName: String = javaCharset.displayName().nn

  def displayName(locale: ju.Locale): String = javaCharset.displayName(locale).nn

  def encode(charBuffer: CharBuffer): UIO[ByteBuffer] =
    charBuffer.withJavaBuffer(jBuf => ZIO.succeed(Buffer.byteFromJava(javaCharset.encode(jBuf).nn)))

  override def equals(other: Any): Boolean =
    other match
      case cs: Charset => javaCharset.equals(cs.javaCharset)
      case _           => false

  override def hashCode: Int = javaCharset.hashCode()

  def isRegistered: Boolean = javaCharset.isRegistered

  def name: String = javaCharset.name().nn

  def newDecoder: CharsetDecoder =
    CharsetDecoder.fromJava(javaCharset.newDecoder().nn)

  def newEncoder: CharsetEncoder = CharsetEncoder.fromJava(javaCharset.newEncoder().nn)

  override def toString: String = javaCharset.toString

  def encodeChunk(chunk: Chunk[Char]): UIO[Chunk[Byte]] =
    for
      charBuf <- Buffer.char(chunk)
      byteBuf <- encode(charBuf)
      chunk   <- byteBuf.getChunk().orDie
    yield chunk

  def encodeString(s: String): UIO[Chunk[Byte]] =
    for
      charBuf <- Buffer.char(s)
      byteBuf <- encode(charBuf)
      chunk   <- byteBuf.getChunk().orDie
    yield chunk

  def decodeChunk(chunk: Chunk[Byte]): UIO[Chunk[Char]] =
    for
      byteBuf <- Buffer.byte(chunk)
      charBuf <- decode(byteBuf)
      chunk   <- charBuf.getChunk().orDie
    yield chunk

  def decodeString(chunk: Chunk[Byte]): UIO[String] =
    for
      byteBuf <- Buffer.byte(chunk)
      charBuf <- decode(byteBuf)
      s       <- charBuf.getString
    yield s


object Charset:

  def fromJava(javaCharset: j.Charset): Charset = new Charset(javaCharset)

  val availableCharsets: Map[String, Charset] =
    j.Charset.availableCharsets().nn.asScala.view.mapValues(new Charset(_)).toMap

  val defaultCharset: Charset = fromJava(j.Charset.defaultCharset().nn)

  def forName(name: String): Charset = fromJava(j.Charset.forName(name).nn)

  def isSupported(name: String): Boolean = j.Charset.isSupported(name)

  object Standard:

    import j.StandardCharsets.*

    val utf8: Charset = Charset.fromJava(UTF_8.nn)

    val utf16: Charset = Charset.fromJava(UTF_16.nn)

    val utf16Be: Charset = Charset.fromJava(UTF_16BE.nn)

    val utf16Le: Charset = Charset.fromJava(UTF_16LE.nn)

    val usAscii: Charset = Charset.fromJava(US_ASCII.nn)

    val iso8859_1: Charset = Charset.fromJava(ISO_8859_1.nn)


