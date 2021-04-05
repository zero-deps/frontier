package ftier
package session

import java.security.SecureRandom
import zio.*

private val random = new SecureRandom

def newid: UIO[String] = {
  for {
    bytes <- UIO.succeed(new Array[Byte](16))
    _     <- UIO.succeed(random.nextBytes(bytes))
    str <- IO.effectTotal(bytes._hex._utf8)
  } yield str
}
