package ru.skelantros.coscheduler.ledger

sealed trait LedgerResult

object LedgerResult {
    case object Success extends LedgerResult
    case class Failure(msg: String) extends LedgerResult
}