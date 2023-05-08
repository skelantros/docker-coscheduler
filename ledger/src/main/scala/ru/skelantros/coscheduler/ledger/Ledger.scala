package ru.skelantros.coscheduler.ledger

import cats.Monad
import cats.effect.Async
import cats.implicits._
import doobie.Meta
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.skelantros.coscheduler.model.SessionId

import scala.concurrent.duration.Duration

object Ledger {
    private implicit val finiteDurationMapping = Meta[Long].imap(Duration.fromNanos)(_.toNanos)
    private implicit val sessionIdMapping = Meta[String].imap(SessionId(_))(_.id)
    private implicit val eventMapping = Meta[String].imap(LedgerEvent.unsafe)(_.value)

    private def insertQuery(note: LedgerNote) =
        sql"""
             insert into ledger (session_id, task_title, node_id, event, instant_time, time_after_start)
             values (${note.sessionId}, ${note.taskTitle}, ${note.nodeId}, ${note.event}, ${note.instantTime}, ${note.timeAfterStart})
           """

    def addNote[F[_] : Async](note: LedgerNote)(implicit xa: Transactor[F]): F[Unit] =
        insertQuery(note).update.run.transact(xa) >> Monad[F].unit

    private def selectQuery(sessionId: SessionId) =
        sql"""
             select id, session_id, task_title, node_id, event, instant_time, time_after_start
             from ledger
             where session_id = $sessionId
           """

    def selectBySessionId[F[_]: Async](sessionId: SessionId)(implicit xa: Transactor[F]): F[List[LedgerNote]] =
        selectQuery(sessionId).query[LedgerNote].to[List].transact(xa)
}
