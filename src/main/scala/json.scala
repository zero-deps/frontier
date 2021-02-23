package ftier

import com.fasterxml.jackson._, annotation._, databind.ObjectMapper, module.scala._

val json = {
  val mapper = ObjectMapper()
  mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
  mapper.registerModule(DefaultScalaModule)
  mapper
}