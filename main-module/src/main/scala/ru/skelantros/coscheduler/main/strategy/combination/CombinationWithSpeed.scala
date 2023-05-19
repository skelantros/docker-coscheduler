package ru.skelantros.coscheduler.main.strategy.combination

import scala.collection.immutable.TreeSet

case class CombinationWithSpeed(combination: Combination, speed: Double)

object CombinationWithSpeed {
    implicit val ordering: Ordering[CombinationWithSpeed] = Ordering.by(_.speed)

    def treeSet(coll: Iterable[CombinationWithSpeed]): TreeSet[CombinationWithSpeed] = TreeSet.from(coll)
}