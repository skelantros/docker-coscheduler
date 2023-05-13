package ru.skelantros.coscheduler.main.strategy.combination

import cats.effect.IO
import ru.skelantros.coscheduler.main.implicits._
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithTaskSpeedEstimate}
import ru.skelantros.coscheduler.model.Task
import cats.implicits._

import scala.collection.immutable.TreeSet
import scala.concurrent.duration.FiniteDuration

class SpeedMeasurer(log: String => IO[Unit],
                    schedulingSystem: SchedulingSystem with WithTaskSpeedEstimate,
                    measurementTime: FiniteDuration,
                    measurementAttempts: Int,
                    waitBeforeMeasurementTime: FiniteDuration) {
    def measureCombinationSpeed(combination: Combination): IO[CombinationWithSpeed] = for {
        _ <- log(s"started measuring combination $combination")
        runTasks <- combination.genParMap(_.toList)(schedulingSystem.saveResumeTask)
        _ <- IO.unit.delayBy(waitBeforeMeasurementTime) // возможно не нужно
        taskSpeeds <- runTasks.parMap(schedulingSystem.speedOf(measurementTime, measurementAttempts))
        _ <- log(s"taskSpeeds = $taskSpeeds")
        _ <- runTasks.parMap(schedulingSystem.savePauseTask)
    } yield CombinationWithSpeed(combination, taskSpeeds.sum)

    def measureCombinationSpeeds(tasks: Vector[Task.Created]): IO[TreeSet[CombinationWithSpeed]] =
        makeCombinations(tasks).map(measureCombinationSpeed).toList.sequence.map(CombinationWithSpeed.treeSet)
}
