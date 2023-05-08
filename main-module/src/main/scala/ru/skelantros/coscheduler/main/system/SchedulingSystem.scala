package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.{CpuSet, Node, SessionContext, Task}
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

    def createTask(task: Task.Built, cpuset: Option[CpuSet] = None): IO[Task.Created]
    def startTask(task: Task.Created): IO[Task.Created]
    def pauseTask(task: Task.Created): IO[Task.Created]
    def savePauseTask(task: Task.Created): IO[Task.Created] =
        pauseTask(task).recoverWith {
            case EndpointException(err) =>
                IO.println(s"savePauseTask($task) recovered from error $err") >> IO.pure(task)
        }

    def resumeTask(task: Task.Created): IO[Task.Created]
    def saveResumeTask(task: Task.Created): IO[Task.Created] =
        resumeTask(task).recoverWith {
            case EndpointException(err) =>
                IO.println(s"saveResumeTask($task) recovered from error $err") >> IO.pure(task)
        }

    def stopTask(task: Task.Created): IO[Task.Created]
    def waitForTask(task: Task.Created): IO[Option[TaskLogs]]
    def taskLogs(task: Task.Created): IO[Option[TaskLogs]]

    def isRunning(task: Task.Created): IO[Boolean]

    def initSession(sessionCtx: SessionContext)(node: Node): IO[Unit]
}

object SchedulingSystem {
    type TaskLogs = String
}