package ru.skelantros.coscheduler.main.system
import cats.effect.IO
import cats.implicits._
import ru.skelantros.coscheduler.image.ImageArchive
import ru.skelantros.coscheduler.main.system.SchedulingSystem.TaskLogs
import ru.skelantros.coscheduler.model.{Node, Task}

// TODO очень плохое логирование, прикрутить нормальное: с таймстемпами, через логгер, всё как положено
trait LoggingSchedulingSystem extends SchedulingSystem {
    private def log(msg: => String): IO[Unit] = IO.println(msg)

    abstract override def buildTask(node: Node)(image: ImageArchive, taskName: String): IO[Task.Built] =
        super.buildTask(node)(image, taskName) <* log(s"buildTask($node)($image, $taskName)")

    abstract override def createTask(task: Task.Built): IO[Task.Created] =
        super.createTask(task) <* log(s"createTask($task)")

    abstract override def startTask(task: Task.Created): IO[Task.Created] =
        super.startTask(task) <* log(s"startTask($task)")

    abstract override def pauseTask(task: Task.Created): IO[Task.Created] =
        super.pauseTask(task) <* log(s"pauseTask($task)")

    abstract override def resumeTask(task: Task.Created): IO[Task.Created] =
        super.resumeTask(task) <* log(s"resumeTask($task)")

    abstract override def stopTask(task: Task.Created): IO[Task.Created] =
        super.stopTask(task) <* log(s"stopTask($task)")

    abstract override def waitForTask(task: Task.Created): IO[Option[TaskLogs]] =
        super.waitForTask(task) <* log(s"waitForTask($task)")

    abstract override def taskLogs(task: Task.Created): IO[Option[TaskLogs]] =
        super.taskLogs(task) <* log(s"taskLogs($task)")
}
