package ftier

import scala.quoted.*

inline def mb(inline size: Int): Int = ${ mbCode('size) }

def mbCode(size: Expr[Int])(using Quotes): Expr[Int] =
  Expr(size.valueOrAbort *! 1024 *! 1024)

extension (x: Int)
  def *!(y: Int): Int =
    math.multiplyExact(x, y)
