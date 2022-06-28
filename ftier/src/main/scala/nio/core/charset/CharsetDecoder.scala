package ftier
package nio
package core
package charset

import java.nio.{charset as j}
import java.nio.charset.{MalformedInputException, UnmappableCharacterException}
import zio.*
import zio.stream.{ZChannel, ZPipeline}

final class CharsetDecoder private (val javaDecoder: j.CharsetDecoder) extends AnyVal:

  def averageCharsPerByte: Float = javaDecoder.averageCharsPerByte()

  def charset: Charset = Charset.fromJava(javaDecoder.charset().nn)

  def decode(in: ByteBuffer): IO[j.CharacterCodingException, CharBuffer] =
    in.withJavaBuffer[Any, Throwable, CharBuffer](jBuf => ZIO.attempt(Buffer.charFromJava(javaDecoder.decode(jBuf).nn)))
      .refineToOrDie[j.CharacterCodingException]

  def decode(
    in: ByteBuffer,
    out: CharBuffer,
    endOfInput: Boolean
  ): UIO[CoderResult] =
    in.withJavaBuffer { jIn =>
      out.withJavaBuffer { jOut =>
        ZIO.succeed(
          CoderResult.fromJava(javaDecoder.decode(jIn, jOut, endOfInput).nn)
        )
      }
    }

  def autoDetect: UIO[AutoDetect] =
    ZIO.succeed {
      if javaDecoder.isAutoDetecting then
        if javaDecoder.isCharsetDetected then
          AutoDetect.Detected(Charset.fromJava(javaDecoder.detectedCharset().nn))
        else
          AutoDetect.NotDetected
      else
        AutoDetect.NotSupported
    }

  def flush(out: CharBuffer): UIO[CoderResult] =
    out.withJavaBuffer { jOut =>
      ZIO.succeed(CoderResult.fromJava(javaDecoder.flush(jOut).nn))
    }

  def malformedInputAction: UIO[j.CodingErrorAction] =
    ZIO.succeed(javaDecoder.malformedInputAction().nn)

  def onMalformedInput(errorAction: j.CodingErrorAction): UIO[Unit] =
    ZIO.succeed(javaDecoder.onMalformedInput(errorAction)).unit

  def unmappableCharacterAction: UIO[j.CodingErrorAction] =
    ZIO.succeed(javaDecoder.unmappableCharacterAction().nn)

  def onUnmappableCharacter(errorAction: j.CodingErrorAction): UIO[Unit] =
    ZIO.succeed(javaDecoder.onUnmappableCharacter(errorAction)).unit

  def maxCharsPerByte: Float = javaDecoder.maxCharsPerByte()

  def replacement: UIO[String] = ZIO.succeed(javaDecoder.replacement().nn)

  def replaceWith(replacement: String): UIO[Unit] =
    ZIO.succeed(javaDecoder.replaceWith(replacement)).unit

  def reset: UIO[Unit] = ZIO.succeed(javaDecoder.reset()).unit

  /**
   * Decodes a stream of bytes into characters according to this character set's encoding.
   *
   * @param bufSize The size of the internal buffer used for encoding.
   *                Must be at least 50.
   */
  def transducer(bufSize: Int = 5000): ZPipeline[Any, j.CharacterCodingException, Byte, Char] =
    val push: ZIO[Scope, Nothing, Option[Chunk[Byte]] => IO[j.CharacterCodingException, Chunk[Char]]] =
      for
        _          <- reset
        byteBuffer <- Buffer.byte(bufSize).orDie
        charBuffer <- Buffer.char((bufSize.toFloat * this.averageCharsPerByte).round).orDie
      yield

        def handleCoderResult(coderResult: CoderResult): IO[j.CharacterCodingException, Chunk[Char]] =
          coderResult match
            case CoderResult.Underflow | CoderResult.Overflow =>
              byteBuffer.compact.orDie *>
                charBuffer.flip *>
                charBuffer.getChunk().orDie <*
                charBuffer.clear
            case CoderResult.Malformed(length) =>
              ZIO.fail(new MalformedInputException(length))
            case CoderResult.Unmappable(length) =>
              ZIO.fail(new UnmappableCharacterException(length))

        (_: Option[Chunk[Byte]])
          .map { inChunk =>
            def decodeChunk(inBytes: Chunk[Byte]): IO[j.CharacterCodingException, Chunk[Char]] =
              for
                bufRemaining <- byteBuffer.remaining
                (decodeBytes, remainingBytes) =
                  if inBytes.length > bufRemaining then
                    inBytes.splitAt(bufRemaining)
                  else
                    (inBytes, Chunk.empty)
                _ <- byteBuffer.putChunk(decodeBytes).orDie
                _ <- byteBuffer.flip
                result <- decode(
                            byteBuffer,
                            charBuffer,
                            endOfInput = false
                          )
                decodedChars   <- handleCoderResult(result)
                remainderChars <- if remainingBytes.isEmpty then ZIO.succeed(Chunk.empty) else decodeChunk(remainingBytes)
              yield decodedChars ++ remainderChars

            decodeChunk(inChunk)
          }
          .getOrElse {
            def endOfInput: IO[j.CharacterCodingException, Chunk[Char]] =
              for
                result <- decode(
                            byteBuffer,
                            charBuffer,
                            endOfInput = true
                          )
                decodedChars   <- handleCoderResult(result)
                remainderChars <- if result == CoderResult.Overflow then endOfInput else ZIO.succeed(Chunk.empty)
              yield decodedChars ++ remainderChars
            byteBuffer.flip *> endOfInput.flatMap { decodedChars =>
              def flushRemaining: IO[j.CharacterCodingException, Chunk[Char]] =
                for
                  result         <- flush(charBuffer)
                  decodedChars   <- handleCoderResult(result)
                  remainderChars <- if result == CoderResult.Overflow then flushRemaining else ZIO.succeed(Chunk.empty)
                yield decodedChars ++ remainderChars
              flushRemaining.map(decodedChars ++ _)
            } <* byteBuffer.clear <* charBuffer.clear
          }

    if bufSize < 50 then
      ZPipeline.fromChannel(ZChannel.fromZIO(ZIO.die(new IllegalArgumentException(s"Buffer size is $bufSize, must be >= 50"))))
    else
      ZPipeline.fromPush(push)


object CharsetDecoder:

  def fromJava(javaDecoder: j.CharsetDecoder): CharsetDecoder =
    new CharsetDecoder(javaDecoder)

