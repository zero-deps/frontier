import zero.ext._
import zio._

package object ftier {
  def parseInt(v: String): IO[ParseIntErr.type, Int] = IO.fromOption(v.toIntOption).orElseFail(ParseIntErr)

  def md5(xs: Array[Byte]): Task[String] = {
    import java.security.MessageDigest
    IO.effect(MessageDigest.getInstance("md5")).map(_.digest(xs)).map(_.hex.utf8)
  }

  def uuid(): String = java.util.UUID.randomUUID().toString
}