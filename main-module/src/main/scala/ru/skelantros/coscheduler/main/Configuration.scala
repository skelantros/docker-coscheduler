package ru.skelantros.coscheduler.main

import ru.skelantros.coscheduler.logging.Logger
import ru.skelantros.coscheduler.main.Configuration.{LoggingOptions, MmbwmonOptions, SpeedTest, TasksTest}
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
    tasksTest: Option[TasksTest]
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

    case class MmbwmonOptions(waitBeforeMeasurement: Option[FiniteDuration], attempts: Option[Int], retryDelay: Option[FiniteDuration], threshold: Option[Double])

    case class SpeedTest(tasks: Vector[StrategyTask], params: TaskSpeed, nodeUri: Uri)

    case class TasksTest(tasks: Vector[StrategyTask], nodeUri: Uri, speedParams: Option[TasksTestParams], mmbwmon: Option[TasksTestParams])

    case class TasksTestParams(attempts: Int, delay: FiniteDuration, time: FiniteDuration)
}