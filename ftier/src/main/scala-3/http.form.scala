package ftier
package http

import java.io.IOException
import scala.util.chaining.*
import scala.annotation.tailrec
import zio.*, stream.*, blocking.*

import ext.given

enum FormData:
  case File(name: String, value: Chunk[Byte]) //todo: write to tmp file
  case Param(name: String, value: Chunk[Byte])

def awaitForm(state: HttpState.AwaitForm, chunk: Chunk[Byte]): IO[BadReq.type, HttpState] =
  val body = state.msg.body
  val form = state.msg.form
  val bound = state.bound
  val curr = state.curr
  for
    rn <- IO.effectTotal(Chunk.fromArray("\r\n".getBytes.nn))
    lines <- Stream.fromChunks(body, chunk).transduce(ZTransducer.splitOnChunk(rn)).runCollect
    // do not touch last chunk because it could be part of boundary
    (ls, lastChunk) = lines.splitAt(lines.length - 1).pipe(x => (x._1, x._2.head))
    `--bound` <- IO.effectTotal(Chunk.fromArray(s"--$bound".getBytes.nn))
    `--bound--` <- IO.effectTotal(Chunk.fromArray(s"--$bound--".getBytes.nn))
    s <-
      curr match
        case None =>
          whenNoCurr(ls, lastChunk, `--bound`, `--bound--`, rn, state)
        case Some(curr) =>
          ls.length match
            case 0 =>
              val field: FormData = appendField(curr, lastChunk)
              IO.succeed(state.copy(msg = state.msg.copy(body = Chunk.empty), curr = Some(field)))
            case 1 =>
              val field: FormData = appendField(curr, ls.head)
              if lastChunk == `--bound--` then
                IO.succeed(HttpState.MsgDone(state.msg.copy(body = Chunk.empty, form = state.msg.form :+ field)))
              else
                IO.succeed(state.copy(msg = state.msg.copy(body = lastChunk, form = state.msg.form :+ field), curr = None))
            case _ =>
              val (head, tail) = ls.splitAt(1)
              val field: FormData = appendField(curr, head.head)
              val s: HttpState.AwaitForm = state.copy(msg = state.msg.copy(body = Chunk.empty, form = state.msg.form :+ field), curr = None)
              whenNoCurr(tail, lastChunk, `--bound`, `--bound--`, rn, s)
  yield s

@tailrec
private def whenNoCurr(ls: Chunk[Chunk[Byte]], lastChunk: Chunk[Byte], `--bound`: Chunk[Byte], `--bound--`: Chunk[Byte], rn: Chunk[Byte], state: HttpState.AwaitForm): IO[BadReq.type, HttpState] =
  ls.headOption match
    case None =>
      if lastChunk == `--bound--` then
        IO.succeed(HttpState.MsgDone(state.msg.copy(body = Chunk.empty)))
      else
        IO.succeed(state.copy(msg = state.msg.copy(body = lastChunk)))
    case Some(head) =>
      if head != `--bound` then
        IO.fail(BadReq)
      else
        // get all headers
        ls.indexWhere(_.isEmpty, 0) match
          case -1 =>
            // headers are not received yet
            IO.succeed(state.copy(msg = state.msg.copy(body = ls.foldLeft[Chunk[Byte]](Chunk.empty){ case (s, a) => (s ++ a) ++ rn } ++ lastChunk)))
          case i =>
            // separate headers
            // - x x x _ y y
            val headers = ls.drop(1).take(i-1).map(x => String(x.toArray, "utf8"))
            headers.find(_.startsWith("Content-Disposition: form-data;")) match
              case None =>
                IO.fail(BadReq)
              case Some(disp) =>
                val isFile = """Content-Disposition: form-data; name="[^"]+"; filename="[^"]+"""".r.findFirstIn(disp).isDefined
                """Content-Disposition: form-data; name="([^"]+)"""".r.findFirstMatchIn(disp).map(_.group(1)) match
                  case None =>
                    IO.fail(BadReq)
                  case Some(name) =>
                    val others = ls.drop(i+1)
                    others.length match
                      case 0 =>
                        // no data in ls, lastChunk contains part of data (curr is not finished)
                        val field = writeField(name, isFile, lastChunk)
                        IO.succeed(state.copy(msg = state.msg.copy(body = Chunk.empty), curr = Some(field)))
                      case 1 =>
                        // all data is here
                        val field = writeField(name, isFile, others.head)
                        if lastChunk == `--bound--` then
                          IO.succeed(HttpState.MsgDone(state.msg.copy(body = Chunk.empty, form = state.msg.form :+ field)))
                        else
                          IO.succeed(state.copy(msg = state.msg.copy(body = lastChunk, form = state.msg.form :+ field)))
                      case _ =>
                        val (head, tail) = others.splitAt(1)
                        val field = writeField(name, isFile, head.head)
                        val s: HttpState.AwaitForm = state.copy(msg = state.msg.copy(body = Chunk.empty, form = state.msg.form :+ field))
                        whenNoCurr(tail, lastChunk, `--bound`, `--bound--`, rn, s)

private def writeField(name: String, isFile: Boolean, value: Chunk[Byte]): FormData =
  if isFile then
    FormData.File(name, value) //todo
  else
    FormData.Param(name, value)

private def appendField(field: FormData, value: Chunk[Byte]): FormData =
  field match
    case x: FormData.File =>
      x.copy(value = x.value ++ value) //todo
    case x: FormData.Param =>
      x.copy(value = x.value ++ value)
