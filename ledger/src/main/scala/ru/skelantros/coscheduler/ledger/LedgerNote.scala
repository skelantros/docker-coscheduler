package ru.skelantros.coscheduler.ledger

import cats.effect.IO
import ru.skelantros.coscheduler.model.{Node, SessionContext, SessionId, Task}

import scala.concurrent.duration.FiniteDuration

case class LedgerNote(id: Option[Int] = None,
                      sessionId: SessionId,
                      taskTitle: String,
                      nodeId: String,
                      event: LedgerEvent,
                      instantTime: FiniteDuration,
                      timeAfterStart: FiniteDuration)

object LedgerNote {
    def generate(node: Node)(task: Task, event: LedgerEvent)(implicit sessionCtx: SessionContext): IO[LedgerNote] = for {
        instantTime <- IO.realTime
    } yield LedgerNote(
        sessionId = sessionCtx.sessionId,
        taskTitle = task.title,
        nodeId = node.id,
        event = event,
        instantTime = instantTime,
        timeAfterStart = instantTime - sessionCtx.startTime
    )
}