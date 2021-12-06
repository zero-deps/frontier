package ftier
package http

import java.io.IOException
import java.nio.file.StandardOpenOption
import scala.util.chaining.*
import scala.annotation.tailrec
import zio.*, stream.*, blocking.*
import zio.nio.file.Files
import zio.nio.core.file.Path

import ext.given

enum FormData:
  case File(name: String, path: Path)
  case Param(name: String, value: Chunk[Byte])

def awaitForm(state: HttpState.AwaitForm, chunk: Chunk[Byte]): ZIO[Blocking, BadReq.type | Exception, HttpState] =
  val body = state.body
  val form = state.form
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
      ((curr match
        case None =>
          whenNoCurr(ls, lastChunk, `--bound`, `--bound--`, rn, state)
        case Some(curr) =>
          ls.length match
            case 0 =>
              for
                field <- appendField(curr, lastChunk, rn)
              yield state.copy(body = Chunk.empty, curr = Some(field))
            case 1 =>
              for
                field <- appendField(curr, ls.head, rn)
              yield
                if lastChunk == `--bound--` then
                  HttpState.MsgDone(state.meta, BodyForm(state.form :+ field))
                else
                  state.copy(body = lastChunk, form = state.form :+ field, curr = None)
            case _ =>
              for
                (head, tail) <- IO.succeed(ls.splitAt(1))
                field <- appendField(curr, head.head, rn)
                s: HttpState.AwaitForm = state.copy(body = Chunk.empty, form = state.form :+ field, curr = None)
                s1 <- whenNoCurr(tail, lastChunk, `--bound`, `--bound--`, rn, s)
              yield s1): ZIO[Blocking, BadReq.type | Exception, HttpState])
  yield s

private def whenNoCurr(ls: Chunk[Chunk[Byte]], lastChunk: Chunk[Byte], `--bound`: Chunk[Byte], `--bound--`: Chunk[Byte], rn: Chunk[Byte], state: HttpState.AwaitForm): ZIO[Blocking, BadReq.type | Exception, HttpState] =
  ls.headOption match
    case None =>
      if lastChunk == `--bound--` then
        IO.succeed(HttpState.MsgDone(state.meta, BodyChunk(Chunk.empty)))
      else
        IO.succeed(state.copy(body = lastChunk))
    case Some(head) =>
      if head != `--bound` then
        state.curr match
          case None => IO.fail(BadReq)
          case Some(curr) =>
            ls.length match
              case 0 =>
                for
                  field <- appendField(curr, lastChunk, rn)
                yield state.copy(body = Chunk.empty, curr = Some(field))
              case 1 =>
                for
                  field <- appendField(curr, ls.head, rn)
                yield
                  if lastChunk == `--bound--` then
                    HttpState.MsgDone(state.meta, BodyForm(state.form))
                  else
                    state.copy(body = lastChunk, curr = None)
              case _ =>
                for
                  (head, tail) <- IO.succeed(ls.splitAt(1))
                  field <- appendField(curr, head.head, rn)
                  s: HttpState.AwaitForm = state.copy(body = Chunk.empty)
                  s1 <- whenNoCurr(tail, lastChunk, `--bound`, `--bound--`, rn, s)
                yield s1
      else
        // get all headers
        ls.indexWhere(_.isEmpty, 0) match
          case -1 =>
            // headers are not received yet
            IO.succeed(state.copy(body = ls.foldLeft[Chunk[Byte]](Chunk.empty){ case (s, a) => (s ++ a) ++ rn } ++ lastChunk))
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
                        for
                          field <- writeField(name, isFile, lastChunk)
                        yield state.copy(body = Chunk.empty, curr = Some(field))
                      case 1 =>
                        // all data is here
                        for
                          field <- writeField(name, isFile, others.head)
                        yield
                          if lastChunk == `--bound--` then
                            HttpState.MsgDone(state.meta, BodyForm(state.form :+ field))
                          else
                            state.copy(body = lastChunk, form = state.form :+ field, curr = None)
                      case _ =>
                        for
                          (head, tail) <- IO.succeed(others.splitAt(1))
                          field <- writeField(name, isFile, head.head)
                          (s: HttpState.AwaitForm) = state.copy(body = Chunk.empty, form = state.form :+ field, curr = Some(field))
                          s1 <- whenNoCurr(tail, lastChunk, `--bound`, `--bound--`, rn, s)
                        yield s1

private def writeField(name: String, isFile: Boolean, value: Chunk[Byte]): ZIO[Blocking, Exception, FormData] =
  if isFile then
    for
      uuid <- IO.effectTotal(java.util.UUID.randomUUID.toString)
      path <- Files.createTempFile(uuid)
      _ <- IO.effectTotal(path.toFile.deleteOnExit)
      _ <- Files.writeBytes(path, value)
    yield FormData.File(name, path)
  else
    IO.succeed(FormData.Param(name, value))

private def appendField(field: FormData, value: Chunk[Byte], rn: Chunk[Byte]): ZIO[Blocking, Exception, FormData] =
  field match
    case x: FormData.File =>
      Files.writeBytes(x.path, rn ++ value, StandardOpenOption.APPEND).map(_ => x)
    case x: FormData.Param =>
      IO.succeed(x.copy(value = x.value ++ value))
