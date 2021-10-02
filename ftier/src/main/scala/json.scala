package ftier.json

import zio.*
import com.fasterxml.jackson.*, annotation.*, databind.{JsonNode, ObjectMapper}, module.scala.*

class JCoder(o: ObjectMapper):
  def encode[A](a: A): UIO[Array[Byte]] =
    IO.effectTotal(o.writeValueAsBytes(a).nn)
  
  def tree(c: Chunk[Byte]): UIO[JsonNode] =
    IO.effect(o.readTree(c.toArray).nn).orDie

end JCoder

def jencode[A](a: A): UIO[Array[Byte]] =
  jcoder.flatMap(_.encode(a))

def jtree(c: Chunk[Byte]): UIO[JsonNode] =
  jcoder.flatMap(_.tree(c))

val jcoder: UIO[JCoder] =
  IO.effectTotal{
    val o = ObjectMapper()
    o.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
    o.registerModule(DefaultScalaModule)
    o.nn
    JCoder(o)
  }
