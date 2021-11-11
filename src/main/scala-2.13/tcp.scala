package ftier

import java.nio.channels.{ServerSocketChannel => JServerSocketChannel, SocketChannel => JSocketChannel, CancelledKeyException, ClosedChannelException}
import java.io.IOException
import zio._, clock._, duration._, nio._, core._, core.channels._

object tcp {
    val size2mb = 2 * 1024 * 1024

    type TcpInit = SocketChannel => Task[TcpHandler]
    type TcpHandler = Chunk[Byte] => Task[Unit]

    def getSocketChannel(key: SelectionKey): Task[SocketChannel] =
        key.channel >>= (ch => IO.effect(ch.asInstanceOf[JSocketChannel])) >>= SocketChannel.fromJava
    
    def getServerSocketChannel(key: SelectionKey): Task[ServerSocketChannel] =
        key.channel >>= (ch => IO.effect(ch.asInstanceOf[JServerSocketChannel])) >>= ServerSocketChannel.fromJava
    
    def select[R](selector: Selector, f: SelectionKey => RIO[R, Any]): RIO[R, Unit] = for {
           _ <- selector.select(10.millisecond)
        keys <- selector.selectedKeys
           _ <- ZIO.foreach(keys){ key =>
                    Managed.make(IO.succeed(key))(selector.removeKey(_).ignore).use{ k =>
                        IO.whenM(k.isValid) {
                            f(k).catchAllCause(err =>
                                IO.effect(println(s"key err ${err.prettyPrint}"))
                            )
                        }
                    }
                }
    } yield ()

    def read(buffer: ByteBuffer, key: SelectionKey): Task[(Int, Chunk[Byte])] = for {
        channel  <- getSocketChannel(key)
        chunks   <- Ref.make[Chunk[Byte]](Chunk.empty)
        lastN    <- (for {
                        _ <- buffer.clear
                        n <- channel.read(buffer)
                        _ <- buffer.flip
                        a <- buffer.getChunk()
                        c <- chunks.get
                        _ <- chunks.set(c ++ a)
                    } yield if (c.length > size2mb) -1 else n).catchSome{
                        case _: ClosedChannelException                                                         => IO.succeed(-1)
                        case err: IOException if Option(err.getMessage).exists(_.contains("Connection reset")) => IO.succeed(-1)
                    }.repeatWhile(_ > 0)
        chunks   <- chunks.get
    } yield (lastN, chunks)

    def bind(addr: SocketAddress,
             serverChannel: ServerSocketChannel,
             accessSelector: Selector,
             readSelectors: Vector[Selector],
             init: TcpInit
            ): RIO[Clock, Unit] = for {
        _        <- serverChannel.configureBlocking(false)
        _        <- serverChannel.bind(addr)
        _        <- serverChannel.register(accessSelector, SelectionKey.Operation.Accept)
        worker   <- Ref.make[Int](0)
        afork    <- select(accessSelector, key =>
                        IO.whenM(key.isAcceptable: Task[Boolean]){ for {
                            server  <- getServerSocketChannel(key)
                            channel <- server.accept
                            channel <- IO.fromOption(channel).orElseFail(new CancelledKeyException())
                            _       <- channel.configureBlocking(false)
                            jsocket <- channel.socket
                            _       <- IO.effect(jsocket.setTcpNoDelay(true))
                            n       <- worker.get
                            _       <- worker.set((n + 1) % readSelectors.length)
                            readKey <- channel.register(readSelectors(n), SelectionKey.Operation.Read)
                            handler <- init(channel)
                            _       <- readKey.attach(handler)
                        } yield ()}
                    ).forever.orDie.fork
        _        <- ZIO.collectAll(readSelectors.map(readSelector => Buffer.byte(4096).flatMap(buffer =>
                        select(readSelector, key =>
                            IO.whenM(key.isReadable: Task[Boolean]) {
                                read(buffer, key).flatMap{ case (n, chunk) => 
                                    (for {
                                        channel <- getSocketChannel(key)
                                        _       <- ZIO.when(n < 0)(channel.close)
                                        handler <- key.attachment
                                        handler <- ZIO.fromOption(handler).orElseFail(new RuntimeException("wrong state"))
                                        handler <- ZIO.effect(handler.asInstanceOf[TcpHandler])
                                        _       <- handler(chunk)
                                    } yield ())
                                        .retry(Schedule.spaced(2.millisecond) && Schedule.recurs(3))
                                }
                            }
                        ).forever.orDie.fork
                    )))
        _        <- afork.await
    } yield ()

    def bind(addr: SocketAddress,
             workers: Int,
             init: TcpInit,
            ): RIO[Clock, Unit] =
        ServerSocketChannel.open.toManaged(_.close.orDie).use{ serverChannel =>
            Selector.make.toManaged(_.close.orDie).use{ accessSelector =>
                Managed.collectAll(Vector.fill(workers)(Selector.make.toManaged(_.close.orDie))).use { readSelectors =>
                    bind(addr, serverChannel, accessSelector, readSelectors.to(Vector), init)
                }
            }
        }

    // case class ConnectionPool(connectSelector: Selector, readSelector: Selector)
    // case class Connection(channel: SocketChannel, cp: ConnectionPool)

    // def close(c: Connection): Task[Unit] = c.channel.close

    // def write(c: Connection, data: Chunk[Byte]): Task[Unit] = c.channel.write(data).unit

    // def connect(cp: ConnectionPool, init: TcpInit, addr: SocketAddress): Task[Connection] = for {
    //     channel <- SocketChannel.open(addr)
    //     _       <- channel.configureBlocking(false)
    //     jsocket <- channel.socket
    //     _       <- IO.effect(jsocket.setTcpNoDelay(true))
    //     // cKey <- channel.register(cp.connectSelector, SelectionKey.Operation.Connect)
    //     readKey <- channel.register(cp.readSelector, SelectionKey.Operation.Read)
    //     handler <- init(channel)
    //     _       <- readKey.attach(handler)
    //     // _       <- channel.connect(addr)
    // } yield Connection(channel, cp)

    // val connectionPool: Task[ConnectionPool] = for {
    //     connects <- Selector.make
    //     reads    <- Selector.make
    //     // _        <- select(connects, key =>
    //     //                 IO.whenM(key.isConnectable: Task[Boolean]){ for {
    //     //                     channel <- getSocketChannel(key)
    //     //                     _       <- channel.finishConnect
    //     //                     _       <- channel.register(reads, SelectionKey.Operation.Read)
    //     //                 } yield ()}
    //     //             ).forever.fork
    //     buffer   <- Buffer.byte(4096)
    //     _        <- select(reads, key =>
    //                     IO.whenM(key.isReadable: Task[Boolean]) {
    //                         read(buffer, key).flatMap{ case (n, chunk) => for {
    //                             channel <- getSocketChannel(key)
    //                             _       <- ZIO.when(n < 0)(channel.close)
    //                             handler <- key.attachment
    //                             handler <- ZIO.fromOption(handler).orElseFail(new RuntimeException("wrong state"))
    //                             handler <- ZIO.effect(handler.asInstanceOf[TcpHandler])
    //                             _       <- handler(n < 0, chunk)
    //                         } yield ()}
    //                     }
    //                 ).forever.orDie.fork
    // } yield ConnectionPool(connects, reads)    
}
