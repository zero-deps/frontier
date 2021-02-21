package ftier

import zio.test._, Assertion._

object UrlSpec extends DefaultRunnableSpec:
  def spec = suite("UrlSpec")(
    test("/") {
      assert("/" match
        case "/" => true
        case _ => false
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
        case Root / "a" => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b") {
      assert("/a/b" match
        case Root / "a" / "b" => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b?x=1") {
      assert("/a/b?x=1" match
        case (Root / "a" / "b") ? ("x" * "1") => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b?x=1&y=2") {
      assert("/a/b?x=1&y=2" match
        case (Root / "a" / "b") ? ("x" * "1" & "y" * "2") => true
        case _ => false
      )(equalTo(true))
    }
  )
