package ftier

import java.security.SecureRandom
import zero.ext._
import zio._

object session {
  val random = new SecureRandom

  def newid: UIO[String] = {
    for {
      bytes <- UIO.succeed(new Array[Byte](16))
      _     <- UIO.succeed(random.nextBytes(bytes))
      str <- IO.effectTotal(bytes.hex.utf8)
    } yield str
  }
}
