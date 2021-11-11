package ftier

import zio._

object http {
    case class Request(method: String, url: String, headers: Map[String, String], body: Chunk[Byte]) //Stream[Chunk[Byte]]
    case class Response(code: Int, headers: Map[String, String], body: Chunk[Byte])
    object Response {
        def apply(code: Int, 
                  headers: Map[String, String] = Map.empty, 
                  data: Chunk[Byte] = Chunk.empty): Response = new Response(code, headers, data)
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

    def processChunk(chunk: Chunk[Byte], s: HttpState): IO[HttpErr, HttpState] = s match {
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

    def parseHeader(pos: Int, chunk: Chunk[Byte]): IO[HttpErr, HttpState] = for {
        split    <- IO.succeed(chunk.splitAt(pos + 1))
        (header, body) = split
        lines    <- IO.succeed(new String(header.toArray).split("\r\n").toVector)
        headers  <- IO.succeed(lines.drop(1).map(h => h.split(": ")).collect{ case Array(h, k) => (h,k) }.to(Map))
        line1    <- IO.succeed(lines.headOption.getOrElse(""))
        // queue    <- Queue.bounded[Chunk[Byte]](100)
        // _        <- queue.offer(body)
        // stream   <- IO.succeed(Stream.fromQueueWithShutdown(queue))
        msg      <- IO.succeed(HttpMessage(line1, headers, body))
        len      <- headers.get("Content-Length").map(l => parseInt(l).orElseFail(HttpErr.BadContentLength)).getOrElse(IO.succeed(0))
    } yield {
        if (msg.body.length >= len) {
            MsgDone(msg)
        } else {
            AwaitBody(msg, len)
        }
    }

    def getCookies(req: Request): Option[Map[String, String]] = req.headers.get("Cookie").map(parseCookies)

    def parseCookies(cookies: String): Map[String, String] =
        cookies.split("; ").map(_.split('=')).collect{ case Array(k, v) => (k, v) }.to(Map)

    def toReq(msg: HttpMessage): IO[HttpErr, Request] = {
        msg.line1.split(' ').toVector match {
            case method +: url +: _ => IO.succeed(Request(method, url, msg.headers, msg.body))
            case _ => IO.fail(HttpErr.BadFirstLine)
        }
    }
    
    def toResp(msg: HttpMessage): IO[HttpErr, Response] = {
        msg.line1.split(' ').toVector match {
            case _ +: code +: _ => parseInt(code).orElseFail(HttpErr.BadFirstLine).map(c => Response(c, msg.headers, msg.body))
            case _ => IO.fail(HttpErr.BadFirstLine)
        }
    }

    def build(resp: Response): Chunk[Byte] = {
        val sb = new StringBuilder(16)
        sb.append("HTTP/1.1 ")
        sb.append(resp.code.toString)
        sb.append("\r\n")
        resp.headers.foreach { case (k, v) =>
            sb.append(k)
            sb.append(": ")
            sb.append(v)
            sb.append("\r\n")
        }
        sb.append("\r\n")
        Chunk.fromArray(sb.toString.getBytes("utf-8")) ++ resp.body
    }
    
    def buildUnused(req: Request): Chunk[Byte] = {
        val sb = new StringBuilder(16)
        sb.append(req.method)
        sb.append(" ")
        sb.append(req.url)
        sb.append("HTTP/1.1\r\n")
        req.headers.foreach { case (k, v) =>
            sb.append(k)
            sb.append(": ")
            sb.append(v)
            sb.append("\r\n")
        }
        sb.append("\r\n")
        Chunk.fromArray(sb.toString.getBytes("utf-8")) ++ req.body
    }
}
