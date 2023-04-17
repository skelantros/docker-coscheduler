package ru.skelantros.coscheduler.main.strategy

import cats.effect.IO
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.main.system.SchedulingSystem
import ru.skelantros.coscheduler.model.Node
import cats.implicits._

import java.io.File

trait Strategy {
    def schedulingSystem: SchedulingSystem
    def config: Configuration
    protected def nodes: IO[Vector[Node]] =
        config.nodesUri.map(schedulingSystem.nodeInfo).parSequence
    def execute(tasks: Vector[StrategyTask]): IO[Unit]
}

object Strategy {
    type TaskName = String
    type StrategyTask = (TaskName, File)
}