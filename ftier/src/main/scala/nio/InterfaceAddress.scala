package ftier
package nio
package core

import java.net.{ InterfaceAddress as JinterfaceAddress }

class InterfaceAddress private[nio] (private val jInterfaceAddress: JinterfaceAddress):
  def address: InetAddress = new InetAddress(jInterfaceAddress.getAddress.nn)

  def broadcast: InetAddress = new InetAddress(jInterfaceAddress.getBroadcast.nn)

  def networkPrefixLength: Short = jInterfaceAddress.getNetworkPrefixLength
