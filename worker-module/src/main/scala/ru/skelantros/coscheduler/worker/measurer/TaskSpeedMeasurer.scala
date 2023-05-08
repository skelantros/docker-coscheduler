package ru.skelantros.coscheduler.worker.measurer

import cats.effect.IO
import cats.effect.implicits._
import cats.implicits._
import ru.skelantros.coscheduler.model.Task

import scala.concurrent.duration.FiniteDuration

object TaskSpeedMeasurer {
    def apply(duration: FiniteDuration)(task: Task.Created): IO[Option[Double]] = for {
        measures <- (IPCMeasurer(duration)(task.cpus), CputimeMeasurer(duration)(task.containerId)).parTupled
        _ <- IO.println(s"TaskSeedMeasurer($duration)($task) = $measures")
    } yield measures.mapN(_ * _)
}
