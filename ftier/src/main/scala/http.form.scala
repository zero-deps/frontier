package ftier
package http

import java.io.IOException
import java.nio.file.StandardOpenOption
import java.nio.file.{Files as JFiles, Paths as JPaths}
import scala.util.chaining.*
import scala.annotation.tailrec
import zio.*, stream.*
import zio.nio.file.{Files, Path}

import ext.given
import zio.ZIO.attemptBlocking

enum FormData:
  case File(name: String, path: Path)
  case Param(name: String, value: Chunk[Byte])

private val `\r\n\r\n`  = "\r\n\r\n".getBytes.nn
private val `\r\n`      = "\r\n".getBytes.nn

def awaitForm(state: HttpState.AwaitForm, chunk: Array[Byte]): IO[BadReq.type | Exception, HttpState] =
  val `--bound`   = s"--${state.bound}".getBytes.nn
  val `--bound--` = s"--${state.bound}--".getBytes.nn
  readForm(state, chunk, `--bound`, `--bound--`)

def readForm(state: HttpState.AwaitForm, chunk: Array[Byte], `--bound`: Array[Byte], `--bound--`: Array[Byte]): IO[BadReq.type | Exception, HttpState] =
  
  val `\r\n--bound`   = `\r\n` ++ `--bound`
  val data = state.body ++ chunk

  if data.size == 0 then ZIO.succeed(state)
  else
    state.curr match
      case None =>
        if data.size < `--bound`.size then
          ZIO.succeed(state.copy(body = data))
        else if data startsWith `--bound--` then
          ZIO.succeed(HttpState.MsgDone(state.meta, BodyForm(state.form)))
        else if data startsWith `--bound` then
          data.indexOfSlice(`\r\n\r\n`) match
            case -1 => ZIO.succeed(state.copy(body = data))
            case  i =>
              val (headersChunk, other) = data.splitAt(i)
              for
                headers <- ZIO.succeed(String(headersChunk, "utf8").split("\r\n").nn.drop(1).map(_.nn))
                s       <- 
                  headers.find(_.startsWith("Content-Disposition: form-data;")) match
                    case None =>
                      ZIO.fail(BadReq)
                    case Some(disp) =>
                      val isFile = """Content-Disposition: form-data; name="[^"]+"; filename="[^"]+"""".r.findFirstIn(disp).isDefined
                      """Content-Disposition: form-data; name="([^"]+)"""".r.findFirstMatchIn(disp).map(_.group(1)) match
                        case None       => ZIO.fail(BadReq)
                        case Some(name) =>
                          createField(name, isFile).flatMap[Any, BadReq.type | Exception, HttpState]{ field =>
                            val formDataChunk = other.drop(`\r\n\r\n`.size)
                            awaitForm(state.copy(body = Array.empty, curr = Some(field)), formDataChunk)
                          }
              yield s
        else ZIO.fail(BadReq)
      case Some(param: FormData.Param) =>
        data.indexOfSlice(`\r\n--bound`) match
          case -1 =>
            val (dataToWrite, tail) = data.splitAt(data.size - `\r\n`.length - `--bound--`.length)
            val p = param.copy(value = param.value ++ Chunk.fromArray(dataToWrite))
            ZIO.succeed(state.copy(body = tail, curr = Some(p)))
          case  i => 
            val (paramData, other) = data.splitAt(i)
            val p = param.copy(value = param.value ++ paramData)
            val s = state.copy(body = Array.empty, form = state.form :+ p, curr = None)
            if other startsWith (`\r\n` ++ `--bound--`) then ZIO.succeed(HttpState.MsgDone(s.meta, BodyForm(s.form)))
            else awaitForm(s, other.drop(`\r\n`.size))
      case Some(file: FormData.File) =>
        data.indexOfSlice(`\r\n--bound`) match
          case -1 =>
            val (dataToWrite, tail) = data.splitAt(data.size - `\r\n`.length - `--bound--`.length)
            for _ <- appendFile(file, dataToWrite) yield state.copy(body = tail)
          case  i =>
            val (fileData, other) = data.splitAt(i)
            for
              _  <- appendFile(file, fileData)
              s  = state.copy(body = Array.empty, form = state.form :+ file, curr = None)
              s1 <- 
                if other startsWith (`\r\n` ++ `--bound--`) then ZIO.succeed(HttpState.MsgDone(s.meta, BodyForm(s.form)))
                else awaitForm(s, other.drop(`\r\n`.size))
            yield s1

private def createField(name: String, isFile: Boolean): IO[Exception, FormData] =
  if isFile then
    for
      uuid <- ZIO.succeed(java.util.UUID.randomUUID.toString)
      path <- Files.createTempFile(null.asInstanceOf[String], Some(uuid), Nil)
      _    <- ZIO.succeed(path.toFile.deleteOnExit)
    yield FormData.File(name, path)
  else
    ZIO.succeed(FormData.Param(name, Chunk.empty))

private def appendFile(file: FormData.File, value: Array[Byte]): IO[Exception, Unit] =
  attemptBlocking(JFiles.write(JPaths.get(file.path.toString), value, StandardOpenOption.APPEND)).unit.refineToOrDie[Exception]

