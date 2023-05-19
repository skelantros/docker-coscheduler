package ru.skelantros.coscheduler.main

import ru.skelantros.coscheduler.logging.Logger
import ru.skelantros.coscheduler.main.Configuration.{LoggingOptions, MakeExperiment, MmbwmonOptions, SpeedTest, TasksTest}
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import sttp.model.Uri

import scala.concurrent.duration.{Duration, FiniteDuration}

case class Configuration(
    nodesUri: Vector[Uri],
    tasks: Vector[StrategyTask],
    waitForTaskDelay: Duration,
    taskSpeed: Option[Configuration.TaskSpeed],
    logging: Option[LoggingOptions],
    speedTest: Option[SpeedTest],
    mmbwmon: Option[MmbwmonOptions],
    tasksTest: Option[TasksTest],
    experiment: Option[Experiment],
    makeExperiment: Option[MakeExperiment]
) {
    val schedulingSystemLogging: Logger.Config =
        logging.flatMap(_.schedulingSystem).getOrElse(Logger.defaultConfig)

    val strategyLogging: Logger.Config =
        logging.flatMap(_.strategy).getOrElse(Logger.defaultConfig)
}

object Configuration {
    case class TaskSpeed(measurement: Option[FiniteDuration],
                         waitBeforeMeasurement: Option[FiniteDuration],
                         attempts: Option[Int])

    case class LoggingOptions(
         schedulingSystem: Option[Logger.Config],
         strategy: Option[Logger.Config]
    )

    case class MmbwmonOptions(waitBeforeMeasurement: Option[FiniteDuration], attempts: Option[Int], retryDelay: Option[FiniteDuration], threshold: Option[Double], coresCount: Int)

    case class SpeedTest(tasks: Vector[StrategyTask], params: TaskSpeed, nodeUri: Uri)

    case class TasksTest(tasks: Vector[StrategyTask], nodeUri: Uri, speedParams: Option[TasksTestParams], mmbwmon: Option[TasksTestParams])

    case class TasksTestParams(attempts: Int, delay: FiniteDuration, time: FiniteDuration)

    case class MakeExperiment(tasks: Vector[StrategyTask], count: Int)
}

case class Experiment(combinations: Seq[Experiment.Combination], testCases: Seq[Experiment.TestCase], mmbwmonCores: Int = 1)

object Experiment {
    case class Combination(name: String, tasks: Vector[StrategyTask])
    case class TestCase(title: String, attempts: Int, strategies: TestCaseStrategies, combination: String, randomize: Boolean)

    case class TestCaseStrategies(seq: Boolean = false, seqAll: Boolean = false, fcs: Boolean = false, bw: Boolean = false, fcsHybrid: Boolean = false)
}