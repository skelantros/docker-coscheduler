package ru.skelantros.coscheduler.main.strategy

import cats.effect.IO
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.main.system.SchedulingSystem
import ru.skelantros.coscheduler.model.Node
import cats.implicits._
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}

import java.io.File

trait Strategy extends DefaultLogger {
    def schedulingSystem: SchedulingSystem
    def config: Configuration

    override lazy val loggerConfig: Logger.Config = config.strategyLogging

    protected def nodes: IO[Vector[Node]] =
        config.nodesUri.map(schedulingSystem.nodeInfo).parSequence
    def execute(tasks: Vector[StrategyTask]): IO[Unit]
}

object Strategy {
    type TaskName = String
    type StrategyTask = (TaskName, File)
}