package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.image.{ImageArchive, ImageArchiver}
import ru.skelantros.coscheduler.logging.Logger
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.model.{CpuSet, Node, SessionContext, Task}
import sttp.model.Uri

import java.io.File
import java.util.UUID

trait SchedulingSystem extends Logger {
    def nodeInfo(uri: Uri): IO[Node]

    def buildTask(node: Node)(image: ImageArchive, taskName: String): IO[Task.Built]
    def buildTaskFromDir(node: Node)(imageDir: File, taskName: String): IO[Task.Built] = {
        ImageArchiver[IO](imageDir, UUID.randomUUID().toString.filter(_ != '-'))
            .use(imageArchive => buildTask(node)(imageArchive, taskName))
    }
    def buildTaskFromTuple(node: Node)(strategyTask: StrategyTask): IO[Task.Built] =
        buildTaskFromDir(node)(strategyTask.dir, strategyTask.title)

    def createTask(task: Task.Built, cpuset: Option[CpuSet] = None): IO[Task.Created]
    def startTask(task: Task.Created): IO[Task.Created]
    def pauseTask(task: Task.Created): IO[Task.Created]
    def savePauseTask(task: Task.Created): IO[Task.Created] =
        pauseTask(task).recoverWith {
            case EndpointException(err) =>
                log.debug("")(s"savePauseTask($task) recovered from error $err") >> IO.pure(task)
        }

    def resumeTask(task: Task.Created): IO[Task.Created]
    def saveResumeTask(task: Task.Created): IO[Task.Created] =
        resumeTask(task).recoverWith {
            case EndpointException(err) =>
                log.debug("")(s"saveResumeTask($task) recovered from error $err") >> IO.pure(task)
        }

    def runTaskFromTuple(node: Node)(strategyTask: StrategyTask, cpuSet: Option[CpuSet] = None): IO[Task.Created] = for {
        built <- buildTaskFromTuple(node)(strategyTask)
        created <- createTask(built, cpuSet)
        started <- startTask(created)
    } yield started

    def stopTask(task: Task.Created): IO[Task.Created]

    def waitForTask(task: Task.Created): IO[Long]

    def isRunning(task: Task.Created): IO[Boolean]

    def initSession(sessionCtx: SessionContext)(node: Node): IO[Unit]

    def updateCpus(task: Task.Created, cpuSet: CpuSet): IO[Task.Created]
}