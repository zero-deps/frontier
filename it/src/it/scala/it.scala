package benchmark

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import scala.concurrent.duration.*

class BenchmarkSimulation extends Simulation {
  val httpProtocol = http
    .wsBaseUrl("ws://localhost:9011")

  val scn = scenario("WebSocket")
    .exec(ws("Connect WS").connect("/wsecho"))
    .pause(1)
    .repeat(2, "i") {
      exec(
        ws("Say Hello WS")
          .sendBytes("Hello, I'm robot and this is message #{i}!".getBytes())
          .await(30)(
            ws.checkBinaryMessage("checkName").check(bodyBytes.is("Hello, I'm robot and this is message #{i}!".getBytes()))
          )
      ).pause(1)
    }
    .exec(ws("Close WS").close)

  setUp(scn.inject(atOnceUsers(925)).protocols(httpProtocol))
}
