package zio.nio.channels

import java.io.{ File, RandomAccessFile }

import zio.nio.core.Buffer
import zio.nio.BaseSpec
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, IO, ZIO }

import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec =
    suite("ScatterGatherChannelSpec")(
      test("scattering read") {
        for {
          raf        <- ZIO.succeed(new RandomAccessFile("deps/zio-nio/nio/src/test/resources/scattering_read_test.txt", "r"))
          fileChannel = raf.getChannel
          readLine    = (buffer: Buffer[Byte]) =>
                          for {
                            _     <- buffer.flip
                            array <- buffer.array
                            text   = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
                          } yield text
          buffs      <- ZIO.collectAll(List(Buffer.byte(5), Buffer.byte(5)))
          list       <- FileChannel(fileChannel).use { channel =>
                          for {
                            _    <- channel.readBuffer(buffs)
                            list <- ZIO.collectAll(buffs.map(readLine))
                          } yield list
                        }
        } yield assert(list)(equalTo("Hello" :: "World" :: Nil))
      },
      test("gathering write") {
        for {
          file       <- ZIO.attempt(new File("deps/zio-nio/nio/src/test/resources/gathering_write_test.txt"))
          raf         = new RandomAccessFile(file, "rw")
          fileChannel = raf.getChannel

          buffs <- ZIO.collectAll(
                     List(
                       Buffer.byte(Chunk.fromArray("Hello".getBytes)),
                       Buffer.byte(Chunk.fromArray("World".getBytes))
                     )
                   )
          _     <- FileChannel(fileChannel).use(_.writeBuffer(buffs).unit)
          result = Source.fromFile(file).getLines().toSeq
          _      = file.delete()
        } yield assert(result)(equalTo(Seq("HelloWorld")))
      }
    )
}
