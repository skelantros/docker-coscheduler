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

    private val optSum = (x: Option[Double], y: Option[Double]) => x.map2(y)(_ + _)

    def measureCombinationSpeed(combination: Combination): IO[Option[CombinationWithSpeed]] = for {
        runTasks <- combination.genParMap(_.toList)(schedulingSystem.saveResumeTask)
        _ <- IO.unit.delayBy(waitBeforeMeasurementTime) // возможно не нужно
        taskSpeedsOpt <- runTasks.parMap(schedulingSystem.saveSpeedOf(measurementTime, measurementAttempts))
        totalSpeedOpt = taskSpeedsOpt.foldLeft(Option(0d))(optSum)
        _ <- log(s"${combination.map(_.title).mkString(",")}: taskSpeeds = $taskSpeedsOpt")
        _ <- runTasks.parMap(schedulingSystem.savePauseTask)
    } yield totalSpeedOpt.map(CombinationWithSpeed(combination, _))

    def measureCombinationSpeeds(tasks: Vector[Task.Created]): IO[TreeSet[CombinationWithSpeed]] =
        makeCombinations(tasks).map(measureCombinationSpeed).toList.sequence.map(_.flatten).map(CombinationWithSpeed.treeSet)
}