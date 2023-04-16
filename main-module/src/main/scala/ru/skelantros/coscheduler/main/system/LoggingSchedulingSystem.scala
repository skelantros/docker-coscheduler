package ru.skelantros.coscheduler.main.system
import cats.effect.IO
import cats.implicits._
import ru.skelantros.coscheduler.image.ImageArchive
import ru.skelantros.coscheduler.main.system.SchedulingSystem.TaskLogs
import ru.skelantros.coscheduler.model.{Node, Task}

// TODO очень плохое логирование, прикрутить нормальное: с таймстемпами, через логгер, всё как положено
class LoggingSchedulingSystem(underlying: SchedulingSystem) extends SchedulingSystem {
    private def log(msg: => String): IO[Unit] = IO.println(msg)

    override def buildTask(node: Node)(image: ImageArchive, imageName: Option[String]): IO[Task.Built] =
        underlying.buildTask(node)(image, imageName) <* log(s"buildTask($node)($image, $imageName)")

    override def createTask(task: Task.Built): IO[Task.Created] =
        underlying.createTask(task) <* log(s"createTask($task)")

    override def startTask(task: Task.Created): IO[Task.Created] =
        underlying.startTask(task) <* log(s"startTask($task)")

    override def pauseTask(task: Task.Created): IO[Task.Created] =
        underlying.pauseTask(task) <* log(s"pauseTask($task)")

    override def resumeTask(task: Task.Created): IO[Task.Created] =
        underlying.resumeTask(task) <* log(s"resumeTask($task)")

    override def stopTask(task: Task.Created): IO[Task.Created] =
        underlying.stopTask(task) <* log(s"stopTask($task)")

    override def waitForTask(task: Task.Created): IO[Option[TaskLogs]] =
        underlying.waitForTask(task) <* log(s"waitForTask($task)")

    override def taskLogs(task: Task.Created): IO[Option[TaskLogs]] =
        underlying.taskLogs(task) <* log(s"taskLogs($task)")
}
