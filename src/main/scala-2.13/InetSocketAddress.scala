package zio.nio

import java.net.{InetSocketAddress => JInetSocketAddress}

package object core {
  implicit class SocketAddressExt(x: SocketAddress) {
    def inetSocketAddress: Option[InetSocketAddress] =
      x.jSocketAddress match {
        case x: JInetSocketAddress => Some(new InetSocketAddress(x))
        case _ => None
      }
  }
}
