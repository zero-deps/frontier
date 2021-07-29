package ftier
package http

import scala.annotation.*

import ext.{*, given}

case class Url(p: P, q: String)

object Url:
  def makep(p: String): P =
    val xs = if p.endsWith("/") then List("") else Nil
    makep(p.split('/').toList.filter(_.nonEmpty) ++ xs, P.R)

  @tailrec def makep(xs: List[String], acc: P): P =
    xs match
      case y :: ys => makep(ys, P.S(y, acc))
      case Nil => acc

  def unapply(url: String): Option[Url] =
    url.split('?').toList match
      case p :: Nil =>
        Some(Url(makep(p), ""))
      case p :: q :: Nil =>
        Some(Url(makep(p), q))
      case _ => None

enum P derives CanEqual:
  case S(s: String, n: P)
  case R

object `?`:
  def unapply(x: String): Option[(P, String)] =
    Url.unapply(x).map(url => url.p -> url.q)

object `&`:
  def unapply(q: String): Option[(String,String)] =
    val xs = q.split('&')
    if xs.isEmpty then
      None
    else
      Some((xs.init.mkString("&"), xs.last))

object `*`:
  def unapply(x: String): Option[(String, String)] =
    x.split('=').toList match
      case a :: b :: Nil => Some(a -> b)
      case _ => None

object `/`:
  def unapply(x: P): Option[(P, String)] =
    x match
      case P.S(a, b) => Some((b, a))
      case P.R => None

  def unapply(x: String): Option[(P, String)] =
    Url.unapply(x).map(_.p).flatMap(unapply)

val Root = P.R
