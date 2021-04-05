package ftier

import zio.*
import com.fasterxml.jackson.*, annotation.*, databind.{JsonNode, ObjectMapper}, module.scala.*

val json: UIO[ObjectMapper] =
  IO.effectTotal {
    val mapper = ObjectMapper()
    mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
    mapper.registerModule(DefaultScalaModule)
    mapper.nn
  }

extension (o: UIO[ObjectMapper])
  def encode[A](a: A): UIO[IArray[Byte]] =
    for {
      o1 <- o
      bs <- IO.effectTotal(o1.writeValueAsBytes(a).nn)
    } yield IArray.unsafeFromArray(bs)
  
  def readTree(c: Chunk[Byte]): UIO[JsonNode] =
    for {
      o1 <- o
      r <- IO.effectTotal(o1.readTree(c.toArray).nn)
    } yield r

