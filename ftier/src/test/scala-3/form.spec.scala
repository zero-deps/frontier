package ftier
package http

import zio.*, test.*, Assertion.*
import zio.nio.file.Files
import scala.collection.immutable.ArraySeq

object FormSpec extends DefaultRunnableSpec:
  def spec = suite("FormSpec")(
    testM("one chunk") {
      val form = (
        "------WebKitFormBoundaryAtKqfnKiF0dX7jp6\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"cms2.local_site2_1630938809827.cms\"\r\n" +
        "Content-Type: application/octet-stream\r\n" +
        "\r\n" +
        "abc\r\n" +
        "------WebKitFormBoundaryAtKqfnKiF0dX7jp6\r\n" +
        "Content-Disposition: form-data; name=\"component\"\r\n" +
        "\r\n" +
        "Documents_Edit\r\n" +
        "------WebKitFormBoundaryAtKqfnKiF0dX7jp6--"
      ).getBytes("utf8").nn
      val state: HttpState.AwaitForm = HttpState.AwaitForm(meta=MetaData("POST", "", Map.empty), body=Chunk.fromArray(form), form=Nil, bound="----WebKitFormBoundaryAtKqfnKiF0dX7jp6", curr=None)
      for
        form <-
          awaitForm(state, Chunk.empty).collect("bad state"){
            case HttpState.MsgDone(_, body: BodyForm) => body.x
          }
        f <- form.collectFirst{ case FormData.File("file", p) => Files.readAllBytes(p).map(x => String(x.toArray)) }.getOrElse(IO.fail("no file"))
        c = form.collectFirst{ case FormData.Param("component", p) => String(p.toArray) }
      yield
        assert(f)(equalTo("abc")) &&
        assert(c)(equalTo(Some("Documents_Edit")))
    }
  )
