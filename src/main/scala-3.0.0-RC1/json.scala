package ftier

import com.fasterxml.jackson.*, annotation.*, databind.ObjectMapper, module.scala.*

val json = {
  val mapper = ObjectMapper()
  mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
  mapper.registerModule(DefaultScalaModule)
  mapper
}