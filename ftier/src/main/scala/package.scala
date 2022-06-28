package ftier

extension [A](a: A | Null)
  inline def toOption: Option[A] =
    given [A]: CanEqual[A, A | Null] = CanEqual.derived
    if a == null then None else Some(a)
