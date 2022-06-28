package ftier
package nio
package core.channels

import java.nio.channels.{
  CancelledKeyException,
  SelectableChannel as JSelectableChannel,
  SelectionKey as JSelectionKey
}

import zio.*

object SelectionKey {

  val JustCancelledKeyException: PartialFunction[Throwable, CancelledKeyException] = {
    case e: CancelledKeyException => e
  }

  sealed abstract class Operation(val intVal: Int)

  object Operation {
    case object Read    extends Operation(JSelectionKey.OP_READ)
    case object Write   extends Operation(JSelectionKey.OP_WRITE)
    case object Connect extends Operation(JSelectionKey.OP_CONNECT)
    case object Accept  extends Operation(JSelectionKey.OP_ACCEPT)

    final val fullSet: Set[Operation] = Set(Read, Write, Connect, Accept)

    final def fromInt(ops: Int): Set[Operation] =
      fullSet.filter(op => (ops & op.intVal) != 0)

    final def toInt(set: Set[Operation]): Int =
      set.foldLeft(0)((ops, op) => ops | op.intVal)
  }
}

class SelectionKey(private[nio] val selectionKey: JSelectionKey) {
  import SelectionKey.*

  final val channel: UIO[JSelectableChannel] =
    ZIO.succeed(selectionKey.channel().nn)

  final val selector: UIO[Selector] =
    ZIO.succeed(selectionKey.selector().nn).map(new Selector(_))

  final val isValid: UIO[Boolean] =
    ZIO.succeed(selectionKey.isValid)

  final val cancel: UIO[Unit] =
    ZIO.succeed(selectionKey.cancel())

  final val interestOps: IO[CancelledKeyException, Set[Operation]] =
    ZIO.attempt(selectionKey.interestOps())
      .map(Operation.fromInt(_))
      .refineToOrDie[CancelledKeyException]

  final def interestOps(ops: Set[Operation]): IO[CancelledKeyException, Unit] =
    ZIO.attempt(selectionKey.interestOps(Operation.toInt(ops)))
      .unit
      .refineToOrDie[CancelledKeyException]

  final val readyOps: IO[CancelledKeyException, Set[Operation]] =
    ZIO.attempt(selectionKey.readyOps())
      .map(Operation.fromInt(_))
      .refineToOrDie[CancelledKeyException]

  final def isReadable: IO[CancelledKeyException, Boolean] =
    ZIO.attempt(selectionKey.isReadable()).refineOrDie(JustCancelledKeyException)

  final def isWritable: IO[CancelledKeyException, Boolean] =
    ZIO.attempt(selectionKey.isWritable()).refineOrDie(JustCancelledKeyException)

  final def isConnectable: IO[CancelledKeyException, Boolean] =
    ZIO.attempt(selectionKey.isConnectable()).refineOrDie(JustCancelledKeyException)

  final def isAcceptable: IO[CancelledKeyException, Boolean] =
    ZIO.attempt(selectionKey.isAcceptable()).refineOrDie(JustCancelledKeyException)

  final def attach(ob: Option[AnyRef]): UIO[Option[AnyRef | Null]] =
    ZIO.succeed(Option(selectionKey.attach(ob.orNull)))

  final def attach(ob: AnyRef): UIO[AnyRef | Null] =
    ZIO.succeed(selectionKey.attach(ob))

  final val detach: UIO[Unit] =
    ZIO.succeed(selectionKey.attach(null)).map(_ => ())

  final val attachment: UIO[Option[AnyRef]] =
    ZIO.succeed(selectionKey.attachment()).map(_.toOption)
}
