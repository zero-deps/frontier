package ftier
package http

import zio.*, test.*, Assertion.*
import zio.nio.file.Files
import scala.collection.immutable.ArraySeq

object FormSpec extends DefaultRunnableSpec:
  def spec = suite("FormSpec")(
    testM("two params") {
      val form = (
       """|------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |Component_1
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |Component_2
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6--"""
      ).stripMargin.getBytes("utf8").nn

      val state: HttpState.AwaitForm = HttpState.AwaitForm(meta=MetaData("POST", "", Map.empty), body=Chunk.fromArray(form), form=Nil, bound="----WebKitFormBoundaryAtKqfnKiF0dX7jp6", curr=None)

      for
        form <-
          awaitForm(state, Chunk.empty).collect("bad state"){
            case HttpState.MsgDone(_, body: BodyForm) => body.x
          }
        components =
          form.collect{
            case FormData.Param("component", c) => String(c.toArray, "utf8")
          }.toSet
      yield
          assert(components)(equalTo(Set("Component_1", "Component_2")))
    },
    testM("file one line") {
      val form = (
       """|------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="file"; filename="some_file_name.txt"
          |Content-Type: application/octet-stream
          |
          |abc
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |Documents_Edit
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6--"""
      ).stripMargin.getBytes("utf8").nn
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
    },
    testM("file two lines") {
      val form = (
       """|------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="file"; filename="some_file_name.json"
          |Content-Type: application/octet-stream
          |
          |abc
          |def
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |1tnenopmoC
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6--"""
      ).stripMargin.getBytes("utf8").nn
      val state: HttpState.AwaitForm = HttpState.AwaitForm(meta=MetaData("POST", "", Map.empty), body=Chunk.fromArray(form), form=Nil, bound="----WebKitFormBoundaryAtKqfnKiF0dX7jp6", curr=None)
      for
        form <-
          awaitForm(state, Chunk.empty).collect("bad state"){
            case HttpState.MsgDone(_, body: BodyForm) => body.x
          }
        f <- form.collectFirst{ case FormData.File("file", p) => Files.readAllBytes(p).map(x => String(x.toArray)) }.getOrElse(IO.fail("no file"))
        c = form.collectFirst{ case FormData.Param("component", p) => String(p.toArray) }
      yield
        assert(f)(equalTo("abc\r\ndef")) &&
        assert(c)(equalTo(Some("1tnenopmoC")))
    },

    testM("two chunks") {
      val form1 = (
       """|------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |Component_numb"""
      ).stripMargin.getBytes("utf8").nn

      val form2 = (
       """er_1
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |Component_number_2
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6--"""
      ).stripMargin.getBytes("utf8").nn

      val state: HttpState.AwaitForm = HttpState.AwaitForm(meta=MetaData("POST", "", Map.empty), body=Chunk.fromArray(form1), form=Nil, bound="----WebKitFormBoundaryAtKqfnKiF0dX7jp6", curr=None)

      for
        s <- awaitForm(state, Chunk.empty).collect("bad state"){ case s: HttpState.AwaitForm => s }
        form <- awaitForm(s, Chunk.fromArray(form2)).collect("bad state"){ case HttpState.MsgDone(_, body: BodyForm) => body.x }
        components =
          form.collect{
            case FormData.Param("component", c) => String(c.toArray, "utf8")
          }.toSet
      yield
          assert(components)(equalTo(Set("Component_number_1", "Component_number_2")))
    },

    testM("more chunks") {
      val form1 = (
       """|------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="file"; filename="some_file_name.txt"
          |Content-Type: application/octet-stream
          |
          |abcd
          |dcba
          |blabl"""
      ).stripMargin.getBytes("utf8").nn
      val form2 = (
       """|abla
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="component"
          |
          |SomeData11
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="component"
          |
          |SomeData22
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposit"""
      ).stripMargin.getBytes("utf8").nn
      val form3 = (
       """|ion: form-data; name="component"
          |
          |SomeData33
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="component"
          |
          |SomeData44"""
      ).stripMargin.getBytes("utf8").nn
      val form4 = (
       """
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="component"
          |
          |SomeData55
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="component"
          |"""
      ).stripMargin.getBytes("utf8").nn
      val form5 = (
       """
          |SomeData66
          |------WebKitFormBoundarymLsm7T4fqzAYLseD
          |Content-Disposition: form-data; name="component"
          |
          |SomeData77
          |------WebKitFormBoundarymLsm7T4fqzAYLseD--"""
      ).stripMargin.getBytes("utf8").nn

      val state: HttpState.AwaitForm = HttpState.AwaitForm(meta=MetaData("POST", "", Map.empty), body=Chunk.empty, form=Nil, bound="----WebKitFormBoundarymLsm7T4fqzAYLseD", curr=None)

      for
        s1 <- awaitForm(state, Chunk.fromArray(form1)).collect("bad state"){ case s: HttpState.AwaitForm => s }
        s2 <- awaitForm(s1, Chunk.fromArray(form2)).collect("bad state"){ case s: HttpState.AwaitForm => s }
        s3 <- awaitForm(s2, Chunk.fromArray(form3)).collect("bad state"){ case s: HttpState.AwaitForm => s }
        s4 <- awaitForm(s3, Chunk.fromArray(form4)).collect("bad state"){ case s: HttpState.AwaitForm => s }
        form <- awaitForm(s4, Chunk.fromArray(form5)).collect("bad state"){ case HttpState.MsgDone(_, body: BodyForm) => body.x }
        components =
          form.collect{
            case FormData.Param("component", c) => String(c.toArray, "utf8")
          }.toSet
        f <- form.collectFirst{ case FormData.File("file", p) => Files.readAllBytes(p).map(x => String(x.toArray)) }.getOrElse(IO.fail("no file"))
      yield
        assert(f)(equalTo("abcd\r\ndcba\r\nblablabla")) &&
        assert(components)(equalTo(Set("SomeData11", "SomeData22", "SomeData22", "SomeData33", "SomeData44", "SomeData55", "SomeData66", "SomeData77")))
    },

    testM("boundary devided") {
      val form1 = (
       """|------WebKitFormBoundaryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |Data_data_1
          |------WebKitFormBoun"""
      ).stripMargin.getBytes("utf8").nn

      val form2 = (
       """daryAtKqfnKiF0dX7jp6
          |Content-Disposition: form-data; name="component"
          |
          |2_data_Data
          |------WebKitFormBoundaryAtKqfnKiF0dX7jp6--"""
      ).stripMargin.getBytes("utf8").nn

      val state: HttpState.AwaitForm = HttpState.AwaitForm(meta=MetaData("POST", "", Map.empty), body=Chunk.fromArray(form1), form=Nil, bound="----WebKitFormBoundaryAtKqfnKiF0dX7jp6", curr=None)

      for
        s <- awaitForm(state, Chunk.empty).collect("bad state"){ case s: HttpState.AwaitForm => s }
        form <- awaitForm(s, Chunk.fromArray(form2)).collect("bad state"){ case HttpState.MsgDone(_, body: BodyForm) => body.x }
        components =
          form.collect{
            case FormData.Param("component", c) => String(c.toArray, "utf8")
          }.toSet
      yield
          assert(components)(equalTo(Set("Data_data_1", "2_data_Data")))
    },
  )
