package ftier.ext

import zio.*
import scala.reflect.ClassTag

def md5(xs: Array[Byte]): Task[String] =
  import java.security.MessageDigest
  IO.effect(MessageDigest.getInstance("md5").nn).map(_.digest(xs).nn).map(_.hex.utf8)

def uuid(): String = java.util.UUID.randomUUID().toString

extension [A](a: A | Null)
  inline def toOption: Option[A] = if a == null then None else Some(a)

extension (xs: Array[Byte])
  inline def utf8: String = String(xs, "utf8")
  inline def hex: Array[Byte] =
    val acc = new Array[Byte](xs.length * 2)
    var i = 0
    while (i < xs.length) {
      val v = xs(i) & 0xff
      acc(i * 2) = hexs(v >>> 4)
      acc(i * 2 + 1) = hexs(v & 0x0f)
      i += 1
    }
    acc

private val hexs = "0123456789abcdef".getBytes("ascii").nn

given [A]: CanEqual[None.type, Option[A]] = CanEqual.derived
given [A, B]: CanEqual[A, A | B] = CanEqual.derived
