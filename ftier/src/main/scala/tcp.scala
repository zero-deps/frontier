package ftier
package tcp

import zio.*, ZIO.*

val `2 MB`: Int = mb(2)

type TcpInit = SocketChannel => Task[TcpHandler]
type TcpHandler = Chunk[Byte] => Task[Unit]

def select(selector: Selector, f: SelectionKey => Task[Any]): Task[Unit] =
  for
    _ <- selector.select(10 millisecond)
    keys <- selector.selectedKeys
    _ <-
      foreach(keys):key =>
        scoped:
          for
            k <- acquireRelease(succeed(key))(selector.removeKey(_).ignore)
            _ <- whenZIO(k.isValidIO):
              f(k)
                .catchSome:
                  case e: IOException if e.getMessage == "Broken pipe" =>
                    succeed(println("IOException: Broken pipe"))
                .catchAllCause:cause =>
                  succeed(println(s"Key error: ${cause.prettyPrint}"))
          yield ()
  yield ()

def read(buffer: ByteBuffer, key: SelectionKey): Task[(Int, Chunk[Byte])] =
  for
    channel <- key.socketChannel
    chunks <- Ref.make[Chunk[Byte]](Chunk.empty)
    lastN <-
      (
        for
          _ <- buffer.clearIO
          n <- channel.read(buffer)
          _ <- buffer.flipIO
          a <- buffer.getChunk
          c <- chunks.get
          _ <- chunks.set(c ++ a)
        yield if c.length > `2 MB` then 0 else n
      )
      .catchSome:
        case _: ClosedChannelException =>
          succeed(-1)
        case err: IOException if err.getMessage.toOption.exists(_.contains("Connection reset")) =>
          succeed(-1)
      .repeatWhile(_ > 0)
    chunks <- chunks.get
  yield (lastN, chunks)

private def bind(
  addr: SocketAddress
, serverChannel: ServerSocketChannel
, accessSelector: Selector
, readSelectors: Vector[Selector]
, init: TcpInit
): Task[Unit] =
  for
    _ <- serverChannel.configureBlocking(false)
    _ <- serverChannel.bind(addr)
    _ <- serverChannel.register(accessSelector, SelectionKey.Op.Accept)
    worker <- Ref.make[Int](0)
    afork <-
      select(
        accessSelector
      , key =>
          whenZIO(key.isAcceptableIO:Task[Boolean]):
            for
              server  <- key.serverSocketChannel
              channel <- server.acceptOrFail
              _ <- channel.configureBlocking(false)
              jsocket <- channel.socket
              _ <- attempt(jsocket.setTcpNoDelay(true))
              n <- worker.get
              _ <- worker.set((n + 1) % readSelectors.length)
              readKey <- channel.register(readSelectors(n), SelectionKey.Op.Read)
              handler <- init(channel)
              _ <- readKey.attachIO(handler)
            yield ()
          ).forever.orDie.fork
    _ <-
      collectAll:
        readSelectors.map:readSelector =>
          ByteBuffer.allocate(4096).flatMap:buffer =>
            select(
              readSelector
            , key => whenZIO(key.isReadableIO:Task[Boolean]):
                read(buffer, key).flatMap:(n, chunk) =>
                  (
                    for
                      channel <- key.socketChannel
                      _ <- when(n < 0)(channel.close)
                      handler <- key.attachmentIO
                      handler <- fromOption(handler).orElseFail(RuntimeException("wrong state"))
                      handler <- attempt(handler.asInstanceOf[TcpHandler])
                      _ <- handler(chunk)
                    yield ()
                  )
                  .retry(Schedule.spaced(2 millisecond) && Schedule.recurs(3))
            ).forever.orDie.fork
    _ <- afork.await
  yield ()

def bind(
  addr: SocketAddress
, workers: Int
, init: TcpInit
): Task[Unit] =
  scoped:
    for
      serverChannel <- acquireRelease(ServerSocketChannel.open)(_.close.orDie)
      accessSelector <- acquireRelease(Selector.make)(_.close.orDie)
      readSelectors <- collectAll(Vector.fill(workers)(acquireRelease(Selector.make)(_.close.orDie)))
      _ <- bind(addr, serverChannel, accessSelector, readSelectors, init)
    yield ()

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
//                             _       <- when(n < 0)(channel.close)
//                             handler <- key.attachment
//                             handler <- fromOption(handler).orElseFail(new RuntimeException("wrong state"))
//                             handler <- effect(handler.asInstanceOf[TcpHandler])
//                             _       <- handler(n < 0, chunk)
//                         } yield ()}
//                     }
//                 ).forever.orDie.fork
// } yield ConnectionPool(connects, reads)    
