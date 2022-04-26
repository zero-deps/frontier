/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.http4s.blaze

import cats.effect._
import cats.effect.std.Queue
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._

import scala.concurrent.duration._

object BlazeWebSocketExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeWebSocketExampleApp[IO].stream.compile.drain.as(ExitCode.Success)
}

class BlazeWebSocketExampleApp[F[_]](implicit F: Async[F]) extends Http4sDsl[F] {
  def routes(wsb: WebSocketBuilder2[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "wsecho" =>
        val echoReply: Pipe[F, WebSocketFrame, WebSocketFrame] =
          _.collect {
            case Binary(msg, _) => Binary(msg)
            case _ => Text("Something new")
          }
        Queue
          .unbounded[F, Option[WebSocketFrame]]
          .flatMap { q =>
            val d: Stream[F, WebSocketFrame] = Stream.fromQueueNoneTerminated(q).through(echoReply)
            val e: Pipe[F, WebSocketFrame, Unit] = _.enqueueNoneTerminated(q)
            wsb.build(d, e)
          }
    }

  def stream: Stream[F, ExitCode] =
    BlazeServerBuilder[F]
      .bindHttp(9011)
      .withHttpWebSocketApp(routes(_).orNotFound)
      .serve
}

object BlazeWebSocketExampleApp {
  def apply[F[_]: Async]: BlazeWebSocketExampleApp[F] =
    new BlazeWebSocketExampleApp[F]
}