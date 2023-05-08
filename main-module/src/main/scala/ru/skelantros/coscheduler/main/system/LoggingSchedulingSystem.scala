package ru.skelantros.coscheduler.main.system
import cats.effect.IO
import cats.implicits._
import ru.skelantros.coscheduler.image.ImageArchive
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.system.SchedulingSystem.TaskLogs
import ru.skelantros.coscheduler.main.system.WithTaskSpeedEstimate.TaskSpeed
import ru.skelantros.coscheduler.model.{CpuSet, Node, Task}
import sttp.model.Uri

import scala.concurrent.duration.FiniteDuration

trait LoggingSchedulingSystem extends SchedulingSystem with WithRamBenchmark with WithTaskSpeedEstimate with DefaultLogger {

    def loggerConfig: Logger.Config

    abstract override def nodeInfo(uri: Uri): IO[Node] =
        super.nodeInfo(uri) <* log.debug("")(s"nodeInfo($uri)")

    abstract override def buildTask(node: Node)(image: ImageArchive, taskName: String): IO[Task.Built] =
        super.buildTask(node)(image, taskName) <* log.debug("")(s"buildTask($node)($image, $taskName)")

    abstract override def createTask(task: Task.Built, cpuset: Option[CpuSet] = None): IO[Task.Created] =
        super.createTask(task, cpuset) <* log.debug("")(s"createTask($task)")

    abstract override def startTask(task: Task.Created): IO[Task.Created] =
        super.startTask(task) <* log.debug("")(s"startTask($task)")

    abstract override def pauseTask(task: Task.Created): IO[Task.Created] =
        super.pauseTask(task) <* log.debug("")(s"pauseTask($task)")

    abstract override def resumeTask(task: Task.Created): IO[Task.Created] =
        super.resumeTask(task) <* log.debug("")(s"resumeTask($task)")

    abstract override def stopTask(task: Task.Created): IO[Task.Created] =
        super.stopTask(task) <* log.debug("")(s"stopTask($task)")

    abstract override def waitForTask(task: Task.Created): IO[Option[TaskLogs]] =
        super.waitForTask(task) <* log.debug("")(s"waitForTask($task)")

    abstract override def taskLogs(task: Task.Created): IO[Option[TaskLogs]] =
        super.taskLogs(task) <* log.debug("")(s"taskLogs($task)")

    abstract override def ramBenchmark(node: Node): IO[Double] = for {
        result <- super.ramBenchmark(node)
        _ <- log.debug("")(s"ramBenchmark($node) = $result")
    } yield result

    override def avgRamBenchmark(node: Node)(attempts: Int): IO[Double] = for {
        result <- super.avgRamBenchmark(node)(attempts)
        _ <- log.debug("")(s"avgRamBenchmark($node) = $result")
    } yield result

    abstract override def speedOf(duration: FiniteDuration)(task: Task.Created): IO[TaskSpeed] = for {
        result <- super.speedOf(duration)(task)
        _ <- log.debug("")(s"speedOf($task)($duration) = $result")
    } yield result
}
