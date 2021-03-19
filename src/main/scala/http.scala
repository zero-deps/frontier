package ftier

import zio.*

object http {
  case class Request(
    method: String
  , url: String
  , headers: Map[String, String]
  , body: Chunk[Byte] //Stream[Chunk[Byte]]
  ):
    lazy val cookies: UIO[Map[String, String]] =
      IO.succeed(
        headers.get("Cookie").map(
          _.split("; ").view.map(_.split('=')).collect{
            case Array(k, v) => (k, v)
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

  def processChunk(chunk: Chunk[Byte], s: HttpState): IO[BadContentLength.type, HttpState] =
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

  def parseHeader(pos: Int, chunk: Chunk[Byte]): IO[BadContentLength.type, HttpState] =
    for {
      split    <- IO.succeed(chunk.splitAt(pos + 1))
      (header, body) = split
      lines    <- IO.succeed(new String(header.toArray).split("\r\n").toVector)
      headers  <- IO.succeed(lines.drop(1).map(h => h.split(": ")).collect{ case Array(h, k) => (h,k) }.to(Map))
      line1    <- IO.succeed(lines.headOption.getOrElse(""))
      // queue    <- Queue.bounded[Chunk[Byte]](100)
      // _        <- queue.offer(body)
      // stream   <- IO.succeed(Stream.fromQueueWithShutdown(queue))
      msg      <- IO.succeed(HttpMessage(line1, headers, body))
      len      <- headers.get("Content-Length").map(l => IO.fromOption(l.toIntOption).orElseFail(BadContentLength)).getOrElse(IO.succeed(0))
    } yield {
      if (msg.body.length >= len) {
          MsgDone(msg)
      } else {
          AwaitBody(msg, len)
      }
    }

  def toReq(msg: HttpMessage): IO[BadFirstLine.type, Request] = {
    msg.line1.split(' ').toVector match {
      case method +: url +: _ => IO.succeed(Request(method, url, msg.headers, msg.body))
      case _ => IO.fail(BadFirstLine)
    }
  }
  
  def toResp(msg: HttpMessage): IO[BadFirstLine.type, Response] = {
    msg.line1.split(' ').toVector match {
      case _ +: code +: _ =>
        IO.fromOption(code.toIntOption).orElseFail(BadFirstLine).map(c => Response(c, msg.headers, msg.body))
      case _ => IO.fail(BadFirstLine)
    }
  }

  def build(resp: Response): Chunk[Byte] = Chunk.fromArray(
    s"""|HTTP/1.1 ${resp.code} \r
        |${resp.headers.map{case (k, v) => s"$k: $v\r\n"}.mkString}\r
        |""".stripMargin.getBytes("UTF-8")) ++ resp.body
  
  def build(req: Request): Chunk[Byte] = Chunk.fromArray(
    s"""${req.method} ${req.url} HTTP/1.1\r
       |${req.headers.map{case (k, v) => s"$k: $v\r\n"}.mkString}\r
       |""".stripMargin.getBytes("UTF-8")
  )
}
