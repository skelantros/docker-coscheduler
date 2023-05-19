package ru.skelantros.coscheduler.main.strategy

import ru.skelantros.coscheduler.model.Task

import scala.collection.immutable.BitSet

package object combination {
    type Combination = Set[Task.Created]

    def makeCombination[A](coll: IndexedSeq[A])(idxs: Set[Int]): Set[A] = idxs map coll

    def makeCombinations[A](coll: IndexedSeq[A]): IndexedSeq[Set[A]] = for {
        x <- 1 until 1 << coll.size
        bitSet = BitSet.fromBitMask(Array(x.toLong))
    } yield makeCombination(coll)(bitSet)
}
