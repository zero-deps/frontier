package zio.nio

import java.net.{InetSocketAddress => JInetSocketAddress}
import zero.ext._, option._

package object core {
  implicit class SocketAddressExt(x: SocketAddress) {
    def inetSocketAddress: Option[InetSocketAddress] =
      x.jSocketAddress match {
        case x: JInetSocketAddress => new InetSocketAddress(x).some
        case _ => none
      }
  }
}
