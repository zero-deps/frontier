package ftier
package http

import zio.test.*, Assertion.*
import zio.test.ZIOSpecDefault

object UrlSpec extends ZIOSpecDefault:
  def spec = suite("UrlSpec")(
    test("/") {
      assert("/" match
        case "/" => true
        // case _ => false
      )(equalTo(true))
    }
  , test("/a") {
      assert("/a" match
        case Root / "a" => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/") {
      assert("/a/" match
        case Root / "a" / "" => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b") {
      assert("/a/b" match
        case Root / "a" / "b" => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a?x=1") {
      assert("/a?x=1" match
        case (Root / "a") ? ("x" * x) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a?x=1&y=2") {
      assert("/a?x=1&y=2" match
        case (Root / "a") ? ("x" * x & "y" * y) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a?x=1&y=2&z=3") {
      "/a?x=1&y=2&z=3" match
        case (Root / "a") ? ("x" * x & "y" * y & "z" * z) =>
          assert(x)(equalTo("1"))
          && assert(y)(equalTo("2"))
          && assert(z)(equalTo("3"))
        case _ =>
          assert(false)(equalTo(true))
    }
  )
