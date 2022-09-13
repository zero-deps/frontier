package ftier

extension (xs: Array[Byte])
  inline def asString: String = String(xs, "utf8")
