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

protected[http] val `\r\n\r\n`  = "\r\n\r\n".getBytes.nn
protected[http] val `\r\n`      = Chunk.fromArray("\r\n".getBytes.nn)

def awaitForm(state: HttpState.AwaitForm, chunk: Chunk[Byte]): ZIO[Blocking, BadReq.type | Exception, HttpState] =
  val `--bound`   = Chunk.fromArray(s"--${state.bound}".getBytes.nn)
  val `--bound--` = Chunk.fromArray(s"--${state.bound}--".getBytes.nn)
  readForm(state, chunk, `--bound`, `--bound--`)

def readForm(state: HttpState.AwaitForm, chunk: Chunk[Byte], `--bound`: Chunk[Byte], `--bound--`: Chunk[Byte]): ZIO[Blocking, BadReq.type | Exception, HttpState] =
  
  val `\r\n--bound` = (`\r\n` ++ `--bound`).toArray
  val data = state.body ++ chunk

  if data.size == 0 then IO.succeed(state)
  else
    state.curr match
      case None =>
        if data startsWith `--bound` then
          data.toArray.indexOfSlice(`\r\n\r\n`) match
            case -1 => IO.succeed(state.copy(body = data))
            case  i =>
              val (headersChunk, other) = data.splitAt(i)
              for
                lines   <- Stream.fromChunk(headersChunk).transduce(ZTransducer.splitOnChunk(`\r\n`)).runCollect
                headers = lines.drop(1).map(x => String(x.toArray, "utf8"))
                s       <- 
                  headers.find(_.startsWith("Content-Disposition: form-data;")) match
                    case None =>
                      IO.fail(BadReq)
                    case Some(disp) =>
                      val isFile = """Content-Disposition: form-data; name="[^"]+"; filename="[^"]+"""".r.findFirstIn(disp).isDefined
                      """Content-Disposition: form-data; name="([^"]+)"""".r.findFirstMatchIn(disp).map(_.group(1)) match
                        case None       => IO.fail(BadReq)
                        case Some(name) =>
                          createField(name, isFile).flatMap[Blocking, BadReq.type | Exception, HttpState]{ field =>
                            val formDataChunk = other.drop(`\r\n\r\n`.size)
                            awaitForm(state.copy(body = Chunk.empty, curr = Some(field)), formDataChunk)
                          }
              yield s
        else IO.fail(BadReq)
      case Some(param: FormData.Param) =>
        data.toArray.indexOfSlice(`\r\n--bound`) match
          case -1 =>
            val p = param.copy(value = param.value ++ data)
            IO.succeed(state.copy(body = Chunk.empty, curr = Some(p)))
          case  i => 
            val (paramData, other) = data.splitAt(i)
            val p = param.copy(value = param.value ++ paramData)
            val s = state.copy(body = Chunk.empty, form = state.form :+ p, curr = None)
            if other startsWith (`\r\n` ++ `--bound--`) then IO.succeed(HttpState.MsgDone(s.meta, BodyForm(s.form)))
            else awaitForm(s, other.drop(`\r\n`.size))
      case Some(file: FormData.File) =>
        data.toArray.indexOfSlice(`\r\n--bound`) match
          case -1 =>
            for _ <- appendFile(file, data) yield state.copy(body = Chunk.empty)
          case  i =>
            val (fileData, other) = data.splitAt(i)
            for
              _  <- appendFile(file, fileData)
              s  = state.copy(body = Chunk.empty, form = state.form :+ file, curr = None)
              s1 <- 
                if other startsWith (`\r\n` ++ `--bound--`) then IO.succeed(HttpState.MsgDone(s.meta, BodyForm(s.form)))
                else awaitForm(s, other.drop(`\r\n`.size))
            yield s1

private def createField(name: String, isFile: Boolean): ZIO[Blocking, Exception, FormData] =
  if isFile then
    for
      uuid <- IO.effectTotal(java.util.UUID.randomUUID.toString)
      path <- Files.createTempFile(uuid)
      _    <- IO.effectTotal(path.toFile.deleteOnExit)
    yield FormData.File(name, path)
  else
    IO.succeed(FormData.Param(name, Chunk.empty))

private def appendFile(file: FormData.File, value: Chunk[Byte]): ZIO[Blocking, Exception, Unit] =
  Files.writeBytes(file.path, value, StandardOpenOption.APPEND)

