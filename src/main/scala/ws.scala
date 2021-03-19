package ftier
package ws

import zio.*, nio.*, core.*
import http.*

sealed trait Msg
case class Text(v: String) extends Msg
case class Binary(v: Chunk[Byte]) extends Msg
case object Open extends Msg
case object Close extends Msg
case object Ping extends Msg
case object Pong extends Msg
case class Unknown(opcode: Int, v: Chunk[Byte]) extends Msg

case class WsHeader(fin: Boolean, opcode: Int, mask: Boolean, maskN: Int, size: Int)
case class WsState(h: Option[WsHeader], data: Chunk[Byte])

case class WsContextData(req: Request, send: Msg => UIO[Unit], close: Task[Unit])
type WsContext = Has[WsContextData]

object Ws {
  def req: ZIO[WsContext, Nothing, Request] = ZIO.access(_.get.req)
  def send(msg: Msg): ZIO[WsContext, Nothing, Unit] = ZIO.accessM(_.get.send(msg))
  def close(): ZIO[WsContext, CloseErr, Unit] = ZIO.accessM(_.get.close.mapError(CloseErr(_)))
}

def getNum(from: Int, size: Int, chunk: Chunk[Byte]): Option[Long] = {
  if (chunk.length > from + size) {
    Some(chunk.drop(from).take(size).foldLeft(0L){ case (acc, v) => (acc << 8) | (v & 0xff) })
  } else {
    None
  }
}

def parseHeader(state: WsState): WsState = {
  state.data.take(2).toArray match {
    case Array(b0, b1) =>
      val fin = (b0 >>> 7) > 0
      val opcode = b0 & 0x7f
      val mask = (b1 >>> 7) > 0
      val size = b1 & 0x7f
      val newState = if (size < 126) {
          state.copy(h=Some(WsHeader(fin, opcode, mask, maskN=0, size=size)), data=state.data.drop(2))
      } else {
          val n = if (size == 126) 2 else 8
          getNum(2, n, state.data).fold(state)(num => state.copy(h=Some(WsHeader(fin, opcode, mask, maskN=0, size=num.toInt)), data=state.data.drop(2 + n)))
      }
      newState.h match {  
          case Some(h) if mask =>
              getNum(0, 4, newState.data).fold(state)(num => newState.copy(h=Some(h.copy(maskN=num.toInt)), data=newState.data.drop(4)))
          case _ => newState
      }
    case _ => state
  }
}

def processMask(mask: Boolean, maskN: Int, payload: Chunk[Byte]): Chunk[Byte] = {
  if (mask) {
    var m = Integer.rotateLeft(maskN, 8)
    payload.map{ v =>
      val res = v ^ (m & 0xff)
      m = Integer.rotateLeft(m, 8)
      res.toByte
    }
  } else {
    payload
  }
}

def read(opcode: Int, payload: Chunk[Byte]): Msg = opcode match {
  case 0x1 => Text(new String(payload.toArray))
  case 0x2 => Binary(payload)
  case 0x8 => Close
  case 0x9 => Ping
  case 0xA => Ping
  case 0xF => Open
  case _   => Unknown(opcode, payload)
}

def write(msg: Msg): Task[ByteBuffer] = {
  msg match {
    case Text(v)   => message(0x1, Chunk.fromArray(v.getBytes("UTF-8")))
    case Binary(v) => message(0x2, v)
    case Close     => message(0x8, Chunk.empty)
    case Ping      => message(0x9, Chunk.empty)
    case Pong      => message(0xA, Chunk.empty)
    case Open      => message(0xB, Chunk.empty)
    case a:Unknown => message(a.opcode, a.v)
  }
}

def message(opcode: Int, payload: Chunk[Byte]): Task[ByteBuffer] = {
  val headerSize = 1
  val payloadSize = payload.size
  val payloadLenSize = if (payloadSize < 126) 1 else if (payloadSize < 65536) 1 + 2 else 1 + 8
  val header = 1 << 7 | opcode
  for {
      buffer   <- Buffer.byte(headerSize + payloadLenSize + payloadSize)
      _        <- buffer.put(header.toByte)
      _        <- if (payloadSize < 126) {
                      buffer.put(payloadSize.toByte)
                  } else if (payloadSize < 65536) {
                      for {
                          _ <- buffer.put(126.toByte)
                          _ <- buffer.putShort(payloadSize.toShort)
                      } yield ()
                  } else  {
                      for {
                          _ <- buffer.put(127.toByte)
                          _ <- buffer.putLong(payloadSize.toLong)
                      } yield ()
                  }
      _        <- buffer.putChunk(payload)
      _        <- buffer.flip
  } yield buffer
}

val guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

case class UpgradeRequest(req: Request, key: String)
object UpgradeRequest {
  def unapply(req: Request): Option[UpgradeRequest] = {
      req.headers.get("Sec-WebSocket-Key").map(UpgradeRequest(req, _))
  }
}

def upgrade(req: UpgradeRequest): UIO[Response] = upgrade(req.key)

def upgrade(key: String): UIO[Response] = {
  import java.util.Base64
  import java.security.MessageDigest
  val crypt = MessageDigest.getInstance("SHA-1")
  crypt.reset()
  crypt.update((key + guid).getBytes("UTF-8"))
  val sha1 = crypt.digest()
  val accept = new String(Base64.getEncoder().encode(sha1))
  IO.succeed(Response(101, Map(
      "Upgrade"              -> "websocket",
      "Connection"           -> "Upgrade",
      "Sec-WebSocket-Accept" -> accept,
  )))
}
