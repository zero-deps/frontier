package ftier
package ws

import zio.*, nio.*, core.*, stream.*

import http.*
import ext.{*, given}

sealed trait Msg
case class Text(v: String, last: Boolean) extends Msg
case class Binary(v: Chunk[Byte], last: Boolean) extends Msg
case object Open extends Msg
case class Close(status: CloseStatus) extends Msg
case object Ping extends Msg
case object Pong extends Msg
case class Unknown(opcode: Int, v: Chunk[Byte]) extends Msg

type CloseStatus = Short
val CLOSE_NORMAL: CloseStatus = 1000
val CLOSED_NO_STATUS: CloseStatus = 1005

case class WsHeader(fin: Boolean, opcode: Int, mask: Boolean, maskN: Int, size: Int)
case class WsState(h: Option[WsHeader], data: Chunk[Byte], fragmentsOpcode: Option[Int])

case class WsContextData(
    req: Request
  , send: Msg => UIO[Unit]
  , sendClose: CloseStatus => Task[Unit]
  , uuid: String
  )
type WsContext = Has[WsContextData]

object Ws {
  def req: URIO[WsContext, Request] = ZIO.access(_.get.req)
  def send(msg: Msg): URIO[WsContext, Unit] = ZIO.accessM(_.get.send(msg))
  def close(status: CloseStatus = CLOSE_NORMAL): URIO[WsContext, Unit] = ZIO.accessM(_.get.sendClose(status).orDie)
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
      val fragmentsOpcode = if !fin && opcode != 0x0
        then Some(opcode)
        else state.fragmentsOpcode
      val newState = if (size < 126) {
          state.copy(h=Some(WsHeader(fin, opcode, mask, maskN=0, size=size)), data=state.data.drop(2), fragmentsOpcode = fragmentsOpcode)
      } else {
          val n = if (size == 126) 2 else 8
          getNum(2, n, state.data).fold(state)(num => state.copy(h=Some(WsHeader(fin, opcode, mask, maskN=0, size=num.toInt)), data=state.data.drop(2 + n), fragmentsOpcode = fragmentsOpcode))
      }
      newState.h match {  
          case Some(h) if mask =>
              getNum(0, 4, newState.data).fold(state)(num => newState.copy(h=Some(h.copy(maskN=num.toInt)), data=newState.data.drop(4), fragmentsOpcode = fragmentsOpcode))
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

def read(opcode: Int, payload: Chunk[Byte], fin: Boolean, fragmentsOpcode: Option[Int]): Task[Msg] = (opcode, fragmentsOpcode) match {
  case (0x0, Some(0x1)) => Task.succeed(Text(new String(payload.toArray), fin))
  case (0x0, Some(0x2)) => Task.succeed(Binary(payload, fin))
  case (0x1, _) => Task.succeed(Text(new String(payload.toArray), fin))
  case (0x2, _) => Task.succeed(Binary(payload, fin))
  case (0x8, _) =>
    for status <- 
      if payload.length >= 2 then Buffer.byte(payload.take(2)) >>= { _.getShort }
      else IO.succeed(CLOSED_NO_STATUS)
    yield Close(status)
  case (0x9, _) => Task.succeed(Ping)
  case (0xA, _) => Task.succeed(Pong)
  case (0xF, _) => Task.succeed(Open)
  case (_  , _) => Task.succeed(Unknown(opcode, payload))
}

def write(msg: Msg): Task[ByteBuffer] = {
  msg match {
    case Text(v, _)    => message(0x1, Chunk.fromArray(v.getBytes("utf8").nn))
    case Binary(v, _)  => message(0x2, v)
    case Close(status) =>
      for
        buffer <- Buffer.byte(2)
        _ <- buffer.putShort(status) *> buffer.flip
        chunk <- buffer.getChunk()
        msg <- message(0x8, chunk)
      yield msg
    case Ping          => message(0x9, Chunk.empty)
    case Pong          => message(0xA, Chunk.empty)
    case Open          => message(0xB, Chunk.empty)
    case a: Unknown    => message(a.opcode, a.v)
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

object UpgradeRequest:
  def unapply(req: Request): Option[UpgradeRequest] =
    req.getHeader("Sec-WebSocket-Key").map(UpgradeRequest(req, _))

def upgrade(req: UpgradeRequest): UIO[Response] = upgrade(req.key)

def upgrade(key: String): UIO[Response] = {
  import java.util.Base64
  import java.security.MessageDigest
  val crypt = MessageDigest.getInstance("SHA-1").nn
  crypt.reset()
  crypt.update((key + guid).getBytes("utf8").nn)
  val sha1 = crypt.digest()
  val accept = String(Base64.getEncoder().nn.encode(sha1))
  IO.succeed(Response(101, Seq(
    "Upgrade" -> "websocket"
  , "Connection" -> "Upgrade"
  , "Sec-WebSocket-Accept" -> accept
  ), BodyChunk(Chunk.empty)))
}
