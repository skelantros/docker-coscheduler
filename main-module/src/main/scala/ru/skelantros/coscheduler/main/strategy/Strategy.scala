package ru.skelantros.coscheduler.main.strategy

import cats.effect.IO
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask

import java.io.File

trait Strategy {
    def execute(tasks: Vector[StrategyTask]): IO[Unit]
}

object Strategy {
    type TaskName = String
    type StrategyTask = (TaskName, File)
}