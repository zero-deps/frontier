package ftier
package http

import zio.*
import util.{*, given}

case class Request(
  method: String
, url: String
, headers: Map[String, String]
, body: Chunk[Byte] //Stream[Chunk[Byte]]
):
  lazy val cookies: UIO[Map[String, String]] =
    IO.succeed(
      headers.get("Cookie").map(
        _.split("; ").nn.view.map(_.nn.split('=').nn).collect{
          case Array(k, v) => (k.nn, v.nn)
        }.toMap
      ).getOrElse(Map.empty)
    )
  def cookie(name: String): UIO[Option[String]] =
    cookies.map(_.get(name))
end Request

case class Response(code: Int, headers: Map[String, String], body: Chunk[Byte])
object Response {
  def apply(
    code: Int
  , headers: Map[String, String] = Map.empty
  , data: Chunk[Byte] = Chunk.empty
  ): Response = new Response(code, headers, data)
}

sealed trait HttpState
object HttpState {
  def apply(): HttpState = AwaitHeader(0, false, Chunk.empty) 
}
case class AwaitHeader(prev: Byte, prevrn: Boolean, data: Chunk[Byte]) extends HttpState
case class HttpMessage(line1: String, headers: Map[String, String], body: Chunk[Byte])
case class AwaitBody(msg: HttpMessage, length: Int/*, curr: Int, q: Queue[Chunk[Byte]]*/) extends HttpState
case class MsgDone(msg: HttpMessage) extends HttpState

//Transfer-Encoding: chunked
//multipart/*

object BadReq

def processChunk(chunk: Chunk[Byte], s: HttpState): IO[BadReq.type, HttpState] =
  s match {
    case state: AwaitHeader =>
      var prev = state.prev
      var prevrn = state.prevrn
      var found = false
      chunk.indexWhere( b => {
          val bInt = b.toInt //fix: comparing Byte with Int
          if (bInt != 13 && bInt != 10) prevrn = false //fix: comparing Byte with Int
          if (bInt == 10 && prev.toInt == 13) { //fix: comparing Byte with Int
              if (prevrn) found = true
              prevrn = true
          }
          prev = b
          found
      }, 0) match {
          case -1  => ZIO.succeed(AwaitHeader(prev, prevrn, state.data ++ chunk))
          case pos => parseHeader(pos + state.data.length, state.data ++ chunk)
      }
    case state: AwaitBody =>
      val msg = state.msg.copy(body = state.msg.body ++ chunk)
      if (msg.body.length >= state.length) {
          ZIO.succeed(MsgDone(msg))
      } else {
          ZIO.succeed(state.copy(msg = msg))
      }
      // state.q.offer(chunk) *> IO.when(newState.curr >= newState.len)(state.q.shutdown) *> ZIO.succeed(newState)
    case _: MsgDone => processChunk(chunk, HttpState())
  }

def parseHeader(pos: Int, chunk: Chunk[Byte]): IO[BadReq.type, HttpState] =
  for {
    split    <- IO.succeed(chunk.splitAt(pos + 1))
    (header, body) = split
    lines    <- IO.succeed(String(header.toArray).split("\r\n").nn.toVector)
    headers  <- IO.succeed(lines.drop(1).map(h => h.nn.split(": ").nn).collect{ case Array(h, k) => (h.nn, k.nn) }.to(Map))
    line1    <- IO.succeed(lines.headOption.getOrElse("").nn)
    // queue    <- Queue.bounded[Chunk[Byte]](100)
    // _        <- queue.offer(body)
    // stream   <- IO.succeed(Stream.fromQueueWithShutdown(queue))
    msg      <- IO.succeed(HttpMessage(line1, headers, body))
    len      <- headers.get("Content-Length").map(l => IO.fromOption(l.toIntOption).orElseFail(BadReq)).getOrElse(IO.succeed(0))
  } yield {
    if (msg.body.length >= len) {
        MsgDone(msg)
    } else {
        AwaitBody(msg, len)
    }
  }

def toReq(msg: HttpMessage): IO[BadReq.type, Request] = {
  msg.line1.split(' ').toVector match {
    case method +: url +: _ => IO.succeed(Request(method, url, msg.headers, msg.body))
    case _ => IO.fail(BadReq)
  }
}

// def toResp(msg: HttpMessage): IO[BadReq.type, Response] = {
//   msg.line1.split(' ').toVector match {
//     case _ +: code +: _ =>
//       IO.fromOption(code.toIntOption).orElseFail(BadReq).map(c => Response(c, msg.headers, msg.body))
//     case _ => IO.fail(BadReq)
//   }
// }

def build(resp: Response): Chunk[Byte] =
  Chunk.fromArray(
    s"""|HTTP/1.1 ${resp.code} \r
        |${resp.headers.map{case (k, v) => s"$k: $v\r\n"}.mkString}\r
        |""".stripMargin.getBytes("UTF-8").nn
  ) ++ resp.body

def build(req: Request): Chunk[Byte] =
  Chunk.fromArray(
    s"""${req.method} ${req.url} HTTP/1.1\r
       |${req.headers.map{case (k, v) => s"$k: $v\r\n"}.mkString}\r
       |""".stripMargin.getBytes("UTF-8").nn
  )
