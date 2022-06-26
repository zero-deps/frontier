package zio.nio


import zio.test.TestAspect
import zio._
import zio.test.ZIOSpecDefault

trait BaseSpec extends ZIOSpecDefault {
  override def aspects = List(TestAspect.timeout(60.seconds))
}
