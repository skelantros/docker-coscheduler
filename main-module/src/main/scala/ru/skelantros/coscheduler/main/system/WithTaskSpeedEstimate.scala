package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.main.system.WithTaskSpeedEstimate.TaskSpeed
import ru.skelantros.coscheduler.model.Task

import scala.concurrent.duration.FiniteDuration

trait WithTaskSpeedEstimate { this: SchedulingSystem =>
    def speedOf(duration: FiniteDuration)(task: Task.Created): IO[TaskSpeed]
}

object WithTaskSpeedEstimate {
    type TaskSpeed = Double
}