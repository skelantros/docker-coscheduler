package ru.skelantros.coscheduler.ledger

sealed abstract class LedgerEvent(val value: String)

object LedgerEvent {
    case object Started extends LedgerEvent("Started")
    case object Paused extends LedgerEvent("Paused")
    case object Resumed extends LedgerEvent("Resumed")
    case object Stopped extends LedgerEvent("Stopped")
    case object Completed extends LedgerEvent("Completed")
    case object Created extends LedgerEvent("Created")

    val events: Seq[LedgerEvent] = Seq(Started, Paused, Resumed, Stopped, Completed, Created)

    private def eventPartial(event: LedgerEvent): PartialFunction[String, LedgerEvent] = {
        case event.value => event
    }

    def unsafe(str: String): LedgerEvent =
        events.map(eventPartial).reduce(_ orElse _)(str)
}