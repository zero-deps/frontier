package ftier
package http

import zio.*, stream.*, blocking.*

case class BodyChunk(x: Chunk[Byte])
case class BodyForm(x: Seq[FormData])
case class BodyStream(x: ZStream[Blocking, Throwable, Byte])