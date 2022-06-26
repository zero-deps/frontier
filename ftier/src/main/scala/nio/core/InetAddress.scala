package zio.nio.core

import java.net.{ InetAddress as JInetAddress }

import zio.*

class InetAddress private[nio] (private[nio] val jInetAddress: JInetAddress) {
  def isMulticastAddress: Boolean = jInetAddress.isMulticastAddress

  def isAnyLocalAddress: Boolean = jInetAddress.isAnyLocalAddress

  def isLoopbackAddress: Boolean = jInetAddress.isLoopbackAddress

  def isLinkLocalAddress: Boolean = jInetAddress.isLinkLocalAddress

  def isSiteLocalAddress: Boolean = jInetAddress.isSiteLocalAddress

  def isMCGlobal: Boolean = jInetAddress.isMCGlobal

  def isMCNodeLocal: Boolean = jInetAddress.isMCNodeLocal

  def isMCLinkLocal: Boolean = jInetAddress.isMCLinkLocal

  def isMCSiteLocal: Boolean = jInetAddress.isMCSiteLocal

  def isMCOrgLocal: Boolean = jInetAddress.isMCOrgLocal

  def isReachable(timeOut: Int): IO[Exception, Boolean] =
    ZIO.attempt(jInetAddress.isReachable(timeOut)).refineToOrDie[Exception]

  def isReachable(
    networkInterface: NetworkInterface,
    ttl: Integer,
    timeout: Integer
  ): IO[Exception, Boolean] =
    ZIO.attempt(jInetAddress.isReachable(networkInterface.jNetworkInterface, ttl, timeout))
      .refineToOrDie[Exception]

  def hostname: String = jInetAddress.getHostName

  def canonicalHostName: String = jInetAddress.getCanonicalHostName

  def address: Array[Byte] = jInetAddress.getAddress
}

object InetAddress {

  def byAddress(bytes: Array[Byte]): IO[Exception, InetAddress] =
    ZIO.attempt(JInetAddress.getByAddress(bytes))
      .refineToOrDie[Exception]
      .map(new InetAddress(_))

  def byAddress(hostname: String, bytes: Array[Byte]): IO[Exception, InetAddress] =
    ZIO.attempt(JInetAddress.getByAddress(hostname, bytes))
      .refineToOrDie[Exception]
      .map(new InetAddress(_))

  def byAllName(hostName: String): IO[Exception, Array[InetAddress]] =
    ZIO.attempt(JInetAddress.getAllByName(hostName))
      .refineToOrDie[Exception]
      .map(_.map(new InetAddress(_)))

  def byName(hostName: String): IO[Exception, InetAddress] =
    ZIO.attempt(JInetAddress.getByName(hostName))
      .refineToOrDie[Exception]
      .map(new InetAddress(_))

  def localHost: IO[Exception, InetAddress] =
    ZIO.attempt(JInetAddress.getLocalHost).refineToOrDie[Exception].map(new InetAddress(_))
}