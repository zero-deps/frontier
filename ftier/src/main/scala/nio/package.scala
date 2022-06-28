package zio.nio

extension (value: Long)
  /**
   * Handle -1 magic number returned by many Java APIs when end of file is reached.
   *
   * @return None for `readCount` < 0, otherwise `Some(readCount)`
   */
  def eofCheck: Option[Long] = if value < 0L then None else Some(value)

given [A]: CanEqual[A, A] = CanEqual.derived

