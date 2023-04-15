package ru.skelantros.coscheduler.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.{Node, Task}
import ru.skelantros.coscheduler.system.SchedulingSystem.TaskLogs

import java.io.File

trait SchedulingSystem {
    def buildTask(node: Node)(taskFile: File): IO[Task.Built]
    def createTask(task: Task.Built): IO[Task.Created]
    def startTask(task: Task.Created): IO[Task.Created]
    def pauseTask(task: Task.Created): IO[Task.Created]
    def resumeTask(task: Task.Created): IO[Task.Created]
    def stopTask(task: Task.Created): IO[Task.Created]
    def taskResult(task: Task.Created): IO[Option[TaskLogs]]
    def taskLogs(task: Task.Created): IO[Option[TaskLogs]]
}

object SchedulingSystem {
    type TaskLogs = String
}