package zio.nio
package core

import java.net.{ InetSocketAddress as JInetSocketAddress, SocketAddress as JSocketAddress }
import zio.*

class SocketAddress private[nio] (private[nio] val jSocketAddress: JSocketAddress) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: SocketAddress => other.jSocketAddress == this.jSocketAddress
      case _                    => false
    }

  override def hashCode(): Int = jSocketAddress.hashCode()

  override def toString: String = jSocketAddress.toString

  def inetSocketAddress: Option[InetSocketAddress] =
    jSocketAddress match {
      case x: JInetSocketAddress => Some(InetSocketAddress(x))
      case _ => None
    }
}

class InetSocketAddress private[nio] (private val jInetSocketAddress: JInetSocketAddress)
    extends SocketAddress(jInetSocketAddress) {
  def port: Int = jInetSocketAddress.getPort

  def hostName: IO[Exception, String] =
    ZIO.attempt(jInetSocketAddress.getHostName).refineToOrDie[Exception]

  def hostString: String = jInetSocketAddress.getHostString

  def isUnresolved: Boolean = jInetSocketAddress.isUnresolved

  final override def toString: String =
    jInetSocketAddress.toString
}

object SocketAddress {

  private[nio] def apply(jSocketAddress: JSocketAddress) =
    jSocketAddress match {
      case inet: JInetSocketAddress =>
        new InetSocketAddress(inet)
      case other                    =>
        new SocketAddress(other)
    }

  def inetSocketAddress(port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress(port)

  def inetSocketAddress(hostname: String, port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress(hostname, port)

  def inetSocketAddress(address: InetAddress, port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress(address, port)

  def unresolvedInetSocketAddress(hostname: String, port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress.createUnresolved(hostname, port)

  private object InetSocketAddress {

    def apply(port: Int): IO[Exception, InetSocketAddress] =
      ZIO.attempt(new JInetSocketAddress(port))
        .refineToOrDie[Exception]
        .map(new InetSocketAddress(_))

    def apply(host: String, port: Int): IO[Exception, InetSocketAddress] =
      ZIO.attempt(new JInetSocketAddress(host, port))
        .refineToOrDie[Exception]
        .map(new InetSocketAddress(_))

    def apply(addr: InetAddress, port: Int): IO[Exception, InetSocketAddress] =
      ZIO.attempt(new JInetSocketAddress(addr.jInetAddress, port))
        .refineToOrDie[Exception]
        .map(new InetSocketAddress(_))

    def createUnresolved(host: String, port: Int): IO[Exception, InetSocketAddress] =
      ZIO.attempt(JInetSocketAddress.createUnresolved(host, port))
        .refineToOrDie[Exception]
        .map(new InetSocketAddress(_))
  }
}
