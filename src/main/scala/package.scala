import zero.ext.*
import zio.*

package object ftier {
  def md5(xs: Array[Byte]): Task[String] = {
    import java.security.MessageDigest
    IO.effect(MessageDigest.getInstance("md5")).map(_.digest(xs)).map(_._hex._utf8)
  }

  def uuid(): String = java.util.UUID.randomUUID().toString
}