package ftier
package http

import zio.*, stream.*

case class BodyChunk(x: Chunk[Byte])
case class BodyForm(x: Seq[FormData])
case class BodyStream(x: ZStream[Any, Throwable, Byte])