package ftier

import annotation._
import zero.ext._, option._

case class Url(p: P, q: List[Q])

object Url:
  def makep(p: String): P =
    makep(p.split('/').toList.filter(_.nonEmpty), P.R)

  @tailrec def makep(xs: List[String], acc: P): P =
    xs match
      case y :: ys => makep(ys, P.S(y, acc))
      case Nil => acc

  def makeq(q: String): List[Q] =
    q.split('&').toList.map(_.split('=').toList).collect{
      case n :: v :: Nil => Q(n, v)}

  def unapply(url: String): Option[Url] =
    url.split('?').toList match
      case p :: Nil =>
        Url(makep(p), nil).some
      case p :: q :: Nil =>
        Url(makep(p), makeq(q)).some
      case _ => none

enum P derives CanEqual:
  case S(s: String, n: P)
  case R

case class Q(n: String, v: String)

given CanEqual[Nothing, Q] = CanEqual.derived

object `?`:
  def unapply(x: String): Option[(P, List[Q])] =
    Url.unapply(x).map(url => url.p -> url.q)

object `&`:
  def unapplySeq(xs: List[Q]): Option[List[(String,String)]] =
    xs.map(q => q.n -> q.v).some

object `/`:
  def unapply(x: P): Option[(P, String)] =
    x match
      case P.S(a, b) => (b, a).some
      case P.R => none

  def unapply(x: String): Option[(P, String)] =
    Url.unapply(x).map(_.p).flatMap(unapply)

val Root = P.R
