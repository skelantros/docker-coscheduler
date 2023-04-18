package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.{Node, Task}
import ru.skelantros.coscheduler.main.system.SchedulingSystem.TaskLogs
import ru.skelantros.coscheduler.image.{ImageArchive, ImageArchiver}
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import sttp.model.Uri

import java.io.File
import java.util.UUID

trait SchedulingSystem {
    def nodeInfo(uri: Uri): IO[Node]

    def buildTask(node: Node)(image: ImageArchive, taskName: String): IO[Task.Built]
    def buildTaskFromDir(node: Node)(imageDir: File, taskName: String): IO[Task.Built] = {
        ImageArchiver[IO](imageDir, UUID.randomUUID().toString.filter(_ != '-'))
            .use(imageArchive => buildTask(node)(imageArchive, taskName))
    }
    def buildTaskFromTuple(node: Node)(strategyTask: StrategyTask): IO[Task.Built] =
        (buildTaskFromDir(node) _).tupled(strategyTask.swap)

    def createTask(task: Task.Built): IO[Task.Created]
    def startTask(task: Task.Created): IO[Task.Created]
    def pauseTask(task: Task.Created): IO[Task.Created]
    def resumeTask(task: Task.Created): IO[Task.Created]
    def stopTask(task: Task.Created): IO[Task.Created]
    def waitForTask(task: Task.Created): IO[Option[TaskLogs]]
    def taskLogs(task: Task.Created): IO[Option[TaskLogs]]
}

object SchedulingSystem {
    type TaskLogs = String
}