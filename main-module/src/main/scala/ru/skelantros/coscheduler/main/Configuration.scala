package ru.skelantros.coscheduler.main

import ru.skelantros.coscheduler.logging.Logger
import ru.skelantros.coscheduler.main.Configuration.LoggingOptions
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.model.Node
import sttp.client3.UriContext
import sttp.model.Uri

import java.io.File
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

case class Configuration(
    nodesUri: Vector[Uri],
    tasks: Vector[StrategyTask],
    waitForTaskDelay: Duration,
    bwThreshold: Option[Double],
    bwRetryDelay: Option[Duration],
    mmbwmonAttempts: Option[Int],
    taskSpeed: Option[Configuration.TaskSpeed],
    logging: Option[LoggingOptions]
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
}