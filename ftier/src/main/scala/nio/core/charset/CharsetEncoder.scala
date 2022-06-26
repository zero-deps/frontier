package zio
package nio
package core
package charset

import java.nio.{ charset as j }
import java.nio.charset.{MalformedInputException, UnmappableCharacterException}
import zio.stream.{ ZChannel, ZPipeline }

final class CharsetEncoder private (val javaEncoder: j.CharsetEncoder) extends AnyVal {

  def averageBytesPerChar: Float = javaEncoder.averageBytesPerChar()

  def charset: Charset = Charset.fromJava(javaEncoder.charset())

  def encode(in: CharBuffer): IO[j.CharacterCodingException, ByteBuffer] =
    in.withJavaBuffer[Any, Throwable, ByteBuffer](jBuf => ZIO.attempt(Buffer.byteFromJava(javaEncoder.encode(jBuf))))
      .refineToOrDie[j.CharacterCodingException]

  def encode(in: CharBuffer, out: ByteBuffer, endOfInput: Boolean): UIO[CoderResult] =
    in.withJavaBuffer { jIn =>
      out.withJavaBuffer(jOut => ZIO.succeed(CoderResult.fromJava(javaEncoder.encode(jIn, jOut, endOfInput))))
    }

  def flush(out: ByteBuffer): UIO[CoderResult] =
    out.withJavaBuffer { jOut =>
      ZIO.succeed(CoderResult.fromJava(javaEncoder.flush(jOut)))
    }

  def malformedInputAction: UIO[j.CodingErrorAction] =
    ZIO.succeed(javaEncoder.malformedInputAction())

  def onMalformedInput(errorAction: j.CodingErrorAction): UIO[Unit] =
    ZIO.succeed(javaEncoder.onMalformedInput(errorAction)).unit

  def unmappableCharacterAction: UIO[j.CodingErrorAction] =
    ZIO.succeed(javaEncoder.unmappableCharacterAction())

  def onUnmappableCharacter(errorAction: j.CodingErrorAction): UIO[Unit] =
    ZIO.succeed(javaEncoder.onUnmappableCharacter(errorAction)).unit

  def maxCharsPerByte: Float = javaEncoder.maxBytesPerChar()

  def replacement: UIO[Chunk[Byte]] = ZIO.succeed(Chunk.fromArray(javaEncoder.replacement()))

  def replaceWith(replacement: Chunk[Byte]): UIO[Unit] =
    ZIO.succeed(javaEncoder.replaceWith(replacement.toArray)).unit

  def reset: UIO[Unit] = ZIO.succeed(javaEncoder.reset()).unit

  /**
   * Encodes a stream of characters into bytes according to this character set's encoding.
   *
   * @param bufSize The size of the internal buffer used for encoding.
   *                Must be at least 50.
   */
  def transducer(bufSize: Int = 5000): ZPipeline[Any, j.CharacterCodingException, Char, Byte] = {
    val push: ZIO[Scope, Nothing, Option[Chunk[Char]] => IO[j.CharacterCodingException, Chunk[Byte]]] = {
      for {
        _          <- reset
        charBuffer <- Buffer.char((bufSize.toFloat / this.averageBytesPerChar).round).orDie
        byteBuffer <- Buffer.byte(bufSize).orDie
      } yield {

        def handleCoderResult(coderResult: CoderResult) =
          coderResult match {
            case CoderResult.Underflow | CoderResult.Overflow =>
              charBuffer.compact.orDie *>
                byteBuffer.flip *>
                byteBuffer.getChunk().orDie <*
                byteBuffer.clear
            case CoderResult.Malformed(length) =>
              ZIO.fail(new MalformedInputException(length))
            case CoderResult.Unmappable(length) =>
              ZIO.fail(new UnmappableCharacterException(length))
          }

        (_: Option[Chunk[Char]])
          .map { inChunk =>
            def encodeChunk(inChars: Chunk[Char]): IO[j.CharacterCodingException, Chunk[Byte]] =
              for {
                bufRemaining <- charBuffer.remaining
                (decodeChars, remainingChars) = {
                  if (inChars.length > bufRemaining)
                    inChars.splitAt(bufRemaining)
                  else
                    (inChars, Chunk.empty)
                }
                _              <- charBuffer.putChunk(decodeChars).orDie
                _              <- charBuffer.flip
                result         <- encode(charBuffer, byteBuffer, endOfInput = false)
                encodedBytes   <- handleCoderResult(result)
                remainderBytes <- if (remainingChars.isEmpty) ZIO.succeed(Chunk.empty) else encodeChunk(remainingChars)
              } yield encodedBytes ++ remainderBytes

            encodeChunk(inChunk)
          }
          .getOrElse {
            def endOfInput: IO[j.CharacterCodingException, Chunk[Byte]] =
              for {
                result         <- encode(charBuffer, byteBuffer, endOfInput = true)
                encodedBytes   <- handleCoderResult(result)
                remainderBytes <- if (result == CoderResult.Overflow) endOfInput else ZIO.succeed(Chunk.empty)
              } yield encodedBytes ++ remainderBytes
            charBuffer.flip *> endOfInput.flatMap { encodedBytes =>
              def flushRemaining: IO[j.CharacterCodingException, Chunk[Byte]] =
                for {
                  result         <- flush(byteBuffer)
                  encodedBytes   <- handleCoderResult(result)
                  remainderBytes <- if (result == CoderResult.Overflow) flushRemaining else ZIO.succeed(Chunk.empty)
                } yield encodedBytes ++ remainderBytes
              flushRemaining.map(encodedBytes ++ _)
            } <* charBuffer.clear <* byteBuffer.clear
          }
      }
    }

    if (bufSize < 50)
      ZPipeline.fromChannel(ZChannel.fromZIO(ZIO.die(new IllegalArgumentException(s"Buffer size is $bufSize, must be >= 50"))))
    else
      ZPipeline.fromPush(push)
  }

}

object CharsetEncoder {

  def fromJava(javaEncoder: j.CharsetEncoder): CharsetEncoder =
    new CharsetEncoder(javaEncoder)

}
