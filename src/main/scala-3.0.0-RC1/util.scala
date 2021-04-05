package ftier

import zio.*

def md5(xs: Array[Byte]): Task[String] = {
  import java.security.MessageDigest
  IO.effect(MessageDigest.getInstance("md5").nn).map(_.digest(xs).nn).map(_._hex._utf8)
}

def uuid(): String = java.util.UUID.randomUUID().toString

given ceno[A]: CanEqual[None.type, Option[A]] = CanEqual.derived
given ceaab[A, B]: CanEqual[A, A | B] = CanEqual.derived
