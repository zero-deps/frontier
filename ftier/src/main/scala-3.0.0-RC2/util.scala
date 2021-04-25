package ftier.util

import zio.*

def md5(xs: Array[Byte]): Task[String] = {
  import java.security.MessageDigest
  IO.effect(MessageDigest.getInstance("md5").nn).map(_.digest(xs).nn).map(_._hex._utf8)
}

def uuid(): String = java.util.UUID.randomUUID().toString

extension [A](x: A | Null)
  inline def toOption: Option[A] = if x == null then None else Some(x)

extension (x: Array[Byte])
  inline def _hex: Array[Byte] =
    val acc = new Array[Byte](x.length * 2)
    var i = 0
    while (i < x.length) {
      val v = x(i) & 0xff
      acc(i * 2) = hexs(v >>> 4)
      acc(i * 2 + 1) = hexs(v & 0x0f)
      i += 1
    }
    acc

  inline def _utf8: String =
    String(x, "utf8")

private val hexs = "0123456789abcdef".getBytes("ascii").nn

given ceno[A]: CanEqual[None.type, Option[A]] = CanEqual.derived
given ceaab[A, B]: CanEqual[A, A | B] = CanEqual.derived
