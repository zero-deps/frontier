package benchmark
package akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.scaladsl.Flow
import scala.util.{Failure, Success}

object AkkahttpWs extends App:
  given system: ActorSystem = ActorSystem("akkahttp-ws")
  import system.dispatcher
  import conf.port
  Http()
    .newServerAt("localhost", port)
    .bind:
      path(conf.path):
        handleWebSocketMessages:
          Flow[Message].collect:
            case bm: BinaryMessage.Strict =>
              BinaryMessage.Strict(bm.data)
            case bm: BinaryMessage.Streamed =>
              BinaryMessage.Streamed(bm.dataStream)
    .onComplete:
      case Success(binding) =>
        println(s"Server is listening on ws://localhost:$port/wsecho")
      case Failure(e) =>
        println(s"Server failed to start, exception=$e")
        system.terminate()
