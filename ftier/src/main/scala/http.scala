package ftier
package http

import java.io.IOException
import scala.util.chaining.*
import scala.annotation.tailrec
import zio.*, stream.*, blocking.*

import ext.given

case class Host(name: String, port: Option[String]=None)

case class Request
  ( method: String
  , url: String
  , headers: Map[String, String]
  , body: BodyChunk | BodyForm
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
      case BodyChunk(c) => String(c.toArray, "utf8")
      case _ => ""
  
  lazy val bodyAsBytes: Array[Byte] =
    body match
      case BodyChunk(c) => c.toArray
      case _ => Array.emptyByteArray

  lazy val form: List[(String, String)] =
    bodyAsString.split('&').filter(_.nonEmpty).map(_.split('=').nn).map(x => x.lift(0).getOrElse("") -> x.lift(1).map(decode).getOrElse("")).toList

  lazy val formData: Seq[FormData] =
    body match
      case BodyForm(xs: Seq[FormData]) => xs
      case _ => Nil
  
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
    new Request(method=method, url=url, headers=headers, body=BodyChunk(Chunk.fromArray(body.getBytes("utf8").nn)))

object Get:
  def unapply(r: Request): Option[String] =
    if r.method == "GET" then Some(r.url) else None

object Post:
  def unapply(r: Request): Option[String] =
    if r.method == "POST" then Some(r.url) else None

case class Response
  ( code: Int
  , headers: Seq[(String, String)]
  , body: BodyChunk | BodyStream
  ):
  lazy val bodyAsBytes: Array[Byte] =
    body match
      case BodyChunk(c) => c.toArray
      case _ => Array.emptyByteArray

object Response:
  def empty(code: Int): Response = new Response(code, Nil, BodyChunk(Chunk.empty))

object HttpState:
  def apply(): HttpState = AwaitHeader(0, false, Chunk.empty) 

enum HttpState:
  case AwaitHeader(prev: Byte, prevrn: Boolean, data: Chunk[Byte])
  case AwaitBody(meta: MetaData, body: Chunk[Byte], length: Int/*, curr: Int, q: Queue[Chunk[Byte]]*/)
  case AwaitForm(meta: MetaData, body: Chunk[Byte], form: Seq[FormData], bound: String, curr: Option[FormData])
  case MsgDone(meta: MetaData, body: BodyChunk | BodyForm)

case class MetaData(method: String, url: String, headers: Map[String, String])

object BadReq

def processChunk(chunk: Chunk[Byte], s: HttpState): ZIO[Blocking, BadReq.type | Exception, HttpState] =
  s match
    case state: HttpState.AwaitHeader =>
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
        case -1  => IO.succeed(HttpState.AwaitHeader(prev, prevrn, state.data ++ chunk))
        case pos => parseHeader(pos + state.data.length, state.data ++ chunk)
    
    case state: HttpState.AwaitBody =>
      val body = state.body ++ chunk
      if body.length >= state.length then
        IO.succeed(HttpState.MsgDone(state.meta, BodyChunk(body)))
      else
        IO.succeed(state.copy(body = body))
      // state.q.offer(chunk) *> IO.when(newState.curr >= newState.len)(state.q.shutdown) *> ZIO.succeed(newState)

    case state: HttpState.AwaitForm =>
      awaitForm(state, chunk)

    case _: HttpState.MsgDone =>
      processChunk(chunk, HttpState())

def parseHeader(pos: Int, chunk: Chunk[Byte]): ZIO[Blocking, BadReq.type | Exception, HttpState] =
  for
    (header, body) <- IO.succeed(chunk.splitAt(pos + 1))
    lines <- IO.succeed(String(header.toArray).split("\r\n").nn.toVector)
    headers <- IO.succeed(lines.drop(1).map(_.nn.split(": ").nn).collect{ case Array(h, k) => (h.nn, k.nn) }.toMap)
    line1 <- IO.succeed(lines.headOption.getOrElse("").nn)
    meta <-
      line1.split(' ').toList match
        case m :: u :: _ => IO.succeed(MetaData(method=m, url=u, headers))
        case _ => IO.fail(BadReq)
    // queue    <- Queue.bounded[Chunk[Byte]](100)
    // _        <- queue.offer(body)
    // stream   <- IO.succeed(Stream.fromQueueWithShutdown(queue))
    len <- headers.get("Content-Length").map(h => IO.fromOption(h.toIntOption).orElseFail(BadReq)).getOrElse(IO.succeed(0))
    bound <-
      IO.effectTotal(
        headers.get("Content-Type").flatMap(_.split("multipart/form-data; boundary=") match
          case Array("", b) => Some(b.nn)
          case _ => None
        )
      )
    s <-
      (if body.length >= len then
        bound match
          case Some(bound) =>
            awaitForm(HttpState.AwaitForm(meta, body, Nil, bound, None), Chunk.empty).collect(BadReq){
              case s: HttpState.MsgDone => s
            }
          case None =>
            IO.succeed(HttpState.MsgDone(meta, BodyChunk(body)))
      else
        bound match
          case Some(bound) =>
            IO.succeed(HttpState.AwaitForm(meta, body, Nil, bound, None))
          case None =>
            IO.succeed(HttpState.AwaitBody(meta, body, len))): ZIO[Blocking, BadReq.type | Exception, HttpState]
  yield s

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
