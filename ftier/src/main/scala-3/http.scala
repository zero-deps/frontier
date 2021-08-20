package ftier
package http

import zio.*, stream.*, blocking.*
import java.io.IOException

enum Body derives CanEqual:
  case Chunked(c: Chunk[Byte])
  case FormData(files: Seq[(String, String)], params: Seq[(String, String)])
  case Empty

case class Host(name: String, port: Option[String]=None)

case class Request
  ( method: String
  , url: String
  , headers: Map[String, String]
  , body: Body
  ):
  private lazy val url1 = url.split('?')
  lazy val path: String = url1.head
  lazy val query: String = url1.lift(1).getOrElse("")
  
  lazy val Host: Host =
    headers.getOrElse("Host", "").split(':').toList match
      case name :: Nil => http.Host(name)
      case name :: port :: Nil => http.Host(name, Some(port))
      case _ => http.Host("")
  lazy val Origin = headers.get("Origin")
  lazy val `If-None-Match` = headers.get("If-None-Match")
  
  def paramValues(x: String): List[String] = params.collect{ case (`x`, v) => decode(v) }
  def param(x: String): Option[String] = params.collectFirst{ case (`x`, v) => v }
  def paramDecoded(key: String): Option[String] = param(key).map(decode)
  lazy val params: List[(String, String)] = query.split('&').filter(_.nonEmpty).map(_.split('=')).map(x => x.lift(0).getOrElse("") -> x.lift(1).getOrElse("")).toList
  
  lazy val bodyAsString: String =
    body match
      case Body.Chunked(c) => String(c.toArray, "utf8")
      case _: Body.FormData => ""
      case Body.Empty => ""
  
  lazy val bodyAsBytes: Array[Byte] =
    body match
      case Body.Chunked(c) => c.toArray
      case _: Body.FormData => Array.emptyByteArray
      case Body.Empty => Array.emptyByteArray

  lazy val form: List[(String, String)] = bodyAsString.split('&').filter(_.nonEmpty).map(_.split('=').nn).map(x => x.lift(0).getOrElse("") -> x.lift(1).map(decode).getOrElse("")).toList
  
  private def decode(x: String): String = java.net.URLDecoder.decode(x, "utf8").nn
  
  lazy val cookies: UIO[Map[String, String]] =
    IO.succeed(_cookies)
  
  def cookie(name: String): UIO[Option[String]] =
    cookies.map(_.get(name))
  
  lazy val _cookies: Map[String, String] =
    headers.get("Cookie").map(
      _.split("; ").nn.view.map(_.nn.split('=').nn).collect{
        case Array(k, v) => (k.nn, v.nn)
      }.toMap
    ).getOrElse(Map.empty)

  def _cookie(name: String): Option[String] =
    _cookies.get(name)
end Request

object Request:
  def apply(method: String, url: String, headers: Map[String, String], body: String): Request =
    new Request(method=method, url=url, headers=headers, body=Body.Chunked(Chunk.fromArray(body.getBytes("utf8").nn)))

object Get:
  def unapply(r: Request): Option[String] =
    if r.method == "GET" then Some(r.url) else None

object Post:
  def unapply(r: Request): Option[String] =
    if r.method == "POST" then Some(r.url) else None

case class Response
  ( code: Int
  , headers: Seq[(String, String)]
  , body: Option[(ZStream[Blocking, Throwable, Byte], OnError, onSuccess)]
  )

object Response:
  def empty(code: Int, headers: Seq[(String, String)]): Response =
    new Response(code, headers, None)
  def apply(code: Int, headers: Seq[(String, String)], body: Array[Byte]): Response =
    new Response(code, headers, Some(Stream.fromChunk(Chunk.fromArray(body)), _ => UIO.unit, _ => UIO.unit))
  def apply(code: Int, headers: Seq[(String, String)], body: Chunk[Byte]): Response =
    new Response(code, headers, Some(Stream.fromChunk(body), _ => UIO.unit, _ => UIO.unit))
  def apply(code: Int, headers: Seq[(String, String)], body: ZStream[Blocking, Throwable, Byte]): Response =
    new Response(code, headers, Some(body, _ => UIO.unit, _ => UIO.unit))
  def apply(code: Int, headers: Seq[(String, String)], body: ZStream[Blocking, Throwable, Byte], onError: OnError, onSuccess: onSuccess): Response =
    new Response(code, headers, Some((body, onError, onSuccess)))

type OnError = Throwable => UIO[Unit]
type onSuccess = Unit => UIO[Unit]

sealed trait HttpState
object HttpState:
  def apply(): HttpState = AwaitHeader(0, false, Chunk.empty) 

case class AwaitHeader(prev: Byte, prevrn: Boolean, data: Chunk[Byte]) extends HttpState
case class AwaitBody(msg: HttpMessage, length: Int/*, curr: Int, q: Queue[Chunk[Byte]]*/) extends HttpState
case class AwaitForm(msg: HttpMessage, separator: Chunk[Byte], next: FormData)
case class MsgDone(msg: HttpMessage) extends HttpState

case class HttpMessage(line1: String, headers: Map[String, String], body: Chunk[Byte])

enum FormData:
  case File(name: String, tmp: String)
  case Param(name: String, value: String)

object BadReq

def processChunk(chunk: Chunk[Byte], s: HttpState): IO[BadReq.type, HttpState] =
  s match
    case state: AwaitHeader =>
      var prev = state.prev
      var prevrn = state.prevrn
      var found = false
      chunk.indexWhere(b => {
        if b != 13 && b != 10 then
          prevrn = false
        if b == 10 && prev == 13 then
          if prevrn then
            found = true
          prevrn = true
        prev = b
        found
      }, 0) match
        case -1  => IO.succeed(AwaitHeader(prev, prevrn, state.data ++ chunk))
        case pos => parseHeader(pos + state.data.length, state.data ++ chunk)
    
    case state: AwaitBody =>
      val msg = state.msg.copy(body = state.msg.body ++ chunk)
      if msg.body.length >= state.length then
        IO.succeed(MsgDone(msg))
      else
        IO.succeed(state.copy(msg = msg))
      // state.q.offer(chunk) *> IO.when(newState.curr >= newState.len)(state.q.shutdown) *> ZIO.succeed(newState)

    case state: AwaitForm =>
      ???

    case _: MsgDone =>
      processChunk(chunk, HttpState())

def parseHeader(pos: Int, chunk: Chunk[Byte]): IO[BadReq.type, HttpState] =
  for
    (header, body) <- IO.succeed(chunk.splitAt(pos + 1))
    lines <- IO.succeed(String(header.toArray).split("\r\n").nn.toVector)
    headers <- IO.succeed(lines.drop(1).map(_.nn.split(": ").nn).collect{ case Array(h, k) => (h.nn, k.nn) }.toMap)
    line1 <- IO.succeed(lines.headOption.getOrElse("").nn)
    // queue    <- Queue.bounded[Chunk[Byte]](100)
    // _        <- queue.offer(body)
    // stream   <- IO.succeed(Stream.fromQueueWithShutdown(queue))
    msg <- IO.succeed(HttpMessage(line1, headers, body))
    len <- headers.get("Content-Length").map(l => IO.fromOption(l.toIntOption).orElseFail(BadReq)).getOrElse(IO.succeed(0))
  yield
    if msg.body.length >= len then
      MsgDone(msg)
    else
      AwaitBody(msg, len)

def toReq(msg: HttpMessage): IO[BadReq.type, Request] =
  msg.line1.split(' ').toVector match
    case method +: url +: _ => IO.succeed(Request(method, url, msg.headers, Body.Chunked(msg.body)))
    case _ => IO.fail(BadReq)

// def toResp(msg: HttpMessage): IO[BadReq.type, Response] = {
//   msg.line1.split(' ').toVector match {
//     case _ +: code +: _ =>
//       IO.fromOption(code.toIntOption).orElseFail(BadReq).map(c => Response(c, msg.headers, msg.body))
//     case _ => IO.fail(BadReq)
//   }
// }

def buildRe(code: Int, headers: Seq[(String, String)]): Chunk[Byte] =
  Chunk.fromArray(Seq(
    s"HTTP/1.1 $code \r\n"
  , headers.map{case (k, v) => s"$k: $v\r\n"}.mkString + "\r\n"
  ).mkString.getBytes("utf8").nn)

def build(req: Request): Chunk[Byte] =
  Chunk.fromArray(Seq(
    s"${req.method} ${req.url} HTTP/1.1\r\n"
  , req.headers.map{case (k, v) => s"$k: $v\r\n"}.mkString + "\r\n"
  ).mkString.getBytes("utf8").nn)
