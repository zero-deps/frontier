package zero.ftier

sealed trait Err

case class TcpErr(e: Throwable) extends RuntimeException(e) with Err

sealed trait HttpErr extends Err
object HttpErr {
  case object BadFirstLine extends HttpErr
  case object BadContentLength extends HttpErr
  case object NotHandled extends HttpErr
  case class BadUri(e: Throwable) extends RuntimeException(e) with HttpErr
}

object WsErr {
  case class WriteMessageErr(e: Throwable) extends RuntimeException(e) with HttpErr
  case class CloseErr(e: Throwable) extends RuntimeException(e) with HttpErr
}

case object ParseIntErr extends Err

trait ForeignErr extends Err
