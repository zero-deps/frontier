package ftier

import zio.test._, Assertion._

object UrlSpec extends DefaultRunnableSpec:
  def spec = suite("UrlSpec")(
    test("/") {
      assert("/" match
        case Url(P.R, Nil) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a") {
      assert("/a" match
        case Url(P.S("a", P.R), Nil) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/") {
      assert("/a/" match
        case Url(P.S("a", P.R), Nil) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b (1)") {
      assert("/a/b" match
        case Url(P.S("b", P.S("a", P.R)), Nil) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b (2)") {
      assert("/a/b" match
        case Root / "a" / "b" => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b?x=1&y=2 (1)") {
      assert("/a/b?x=1&y=2" match
        case Url(P.S("b", P.S("a", P.R)), Q("x", "1") :: Q("y", "2") :: Nil) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b?x=1&y=2 (2)") {
      assert("/a/b?x=1&y=2" match
        case (P.R / "a" / "b") ? List(Q("x", "1"), Q("y", "2")) => true
        case _ => false
      )(equalTo(true))
    }
  , test("/a/b?x=1&y=2 (3)") {
      assert("/a/b?x=1&y=2" match
        case (Root / "a" / "b") ? ("x" -> "1" & "y" -> "2") => true
        case _ => false
      )(equalTo(true))
    }
  )
