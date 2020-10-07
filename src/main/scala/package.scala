package zero

import zio._

package object ftier {
  def parseInt(v: String): IO[ParseIntErr.type, Int] = IO.fromOption(v.toIntOption).orElseFail(ParseIntErr)

  private[this] val hexs = "0123456789abcdef".getBytes("ascii")
  def hex(bytes: Array[Byte]): String = {
    val hexChars = new Array[Byte](bytes.length * 2)
    var i = 0
    while (i < bytes.length) {
        val v = bytes(i) & 0xff
        hexChars(i * 2) = hexs(v >>> 4)
        hexChars(i * 2 + 1) = hexs(v & 0x0f)
        i = i + 1
    }
    new String(hexChars, "utf8")
  }

  def md5(xs: Array[Byte]): Task[String] = {
    import java.security.MessageDigest
    IO.effect(MessageDigest.getInstance("md5")).map(_.digest(xs)).map(hex)
  }

  def uuid(): String = java.util.UUID.randomUUID().toString
}