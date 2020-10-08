package zero.ftier

import java.security.SecureRandom
import zio._

object session {
  val random = new SecureRandom

  def newid: UIO[String] = {
    for {
      bytes <- UIO.succeed(new Array[Byte](16))
      _     <- UIO.succeed(random.nextBytes(bytes))
    } yield hex(bytes)
  }
}
