package ru.skelantros.coscheduler.ledger

import ru.skelantros.coscheduler.model.{Node, Task}

import java.sql.Timestamp

case class LedgerNote(action: LedgerAction,
                      task: Task,
                      node: Node,
                      startTime: Timestamp,
                      endTime: Timestamp,
                      result: LedgerResult)