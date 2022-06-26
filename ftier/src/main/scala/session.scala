package ftier
package session

import java.security.SecureRandom
import zio.*

import ext.{*, given}

private val rnd = SecureRandom()

def newid: UIO[Array[Byte]] =
  for
    xs <- ZIO.succeed(new Array[Byte](32))
    _ <- ZIO.succeed(rnd.nextBytes(xs))
    r <- ZIO.succeed(xs.hex)
  yield r

def newid_utf8: UIO[String] =
  for
    x <- newid
    r <- ZIO.succeed(x.utf8)
  yield r

def _newid: Array[Byte] =
  val xs = new Array[Byte](32)
  rnd.nextBytes(xs)
  xs.hex

def _newid_utf8 = _newid.utf8

