package ftier

import annotation._
import zero.ext._, option._

case class Url(p: P, q: String)

object Url:
  def makep(p: String): P =
    makep(p.split('/').toList.filter(_.nonEmpty), P.R)

  @tailrec def makep(xs: List[String], acc: P): P =
    xs match
      case y :: ys => makep(ys, P.S(y, acc))
      case Nil => acc

  def unapply(url: String): Option[Url] =
    url.split('?').toList match
      case p :: Nil =>
        Url(makep(p), "").some
      case p :: q :: Nil =>
        Url(makep(p), q).some
      case _ => none

enum P derives CanEqual:
  case S(s: String, n: P)
  case R

object `?`:
  def unapply(x: String): Option[(P, String)] =
    Url.unapply(x).map(url => url.p -> url.q)

object `&`:
  def unapplySeq(q: String): Option[List[String]] =
    q.split('&').toList.some

object `*`:
  def unapply(x: String): Option[(String, String)] =
    x.split('=').toList match
      case a :: b :: Nil => (a -> b).some
      case _ => none

object `/`:
  def unapply(x: P): Option[(P, String)] =
    x match
      case P.S(a, b) => (b, a).some
      case P.R => none

  def unapply(x: String): Option[(P, String)] =
    Url.unapply(x).map(_.p).flatMap(unapply)

val Root = P.R
