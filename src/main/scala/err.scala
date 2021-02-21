package ftier

sealed trait Err

case class Throwed(e: Throwable) extends Err

sealed trait HttpErr extends Err
object HttpErr {
  case object BadFirstLine        extends HttpErr
  case object BadContentLength    extends HttpErr
  case object NotHandled          extends HttpErr
  case class BadUri(e: Throwable) extends HttpErr
}

object WsErr {
  case class WriteMessageErr(e: Throwable) extends Err
  case class CloseErr       (e: Throwable) extends Err
}

object TgErr {
  case object BadHash  extends Err
  case object Outdated extends Err
}

object UdpErr {
  case object NoAddr extends Err
}

case object ParseIntErr extends Err

given CanEqual[Err, Err] = CanEqual.derived
