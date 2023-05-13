package ru.skelantros.coscheduler.main.strategy

import cats.effect.{IO, Ref}
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.{StrategyTask, TaskName}
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithTaskSpeedEstimate}
import ru.skelantros.coscheduler.model.{Node, Task}
import cats.implicits._
import cats.effect.implicits._
import ru.skelantros.coscheduler.main.implicits._
import ru.skelantros.coscheduler.main.strategy.FCSHybridStrategy.TasksInfo
import ru.skelantros.coscheduler.main.strategy.combination.{Combination, CombinationWithSpeed, SpeedMeasurer}

import java.io.File
import scala.collection.immutable.TreeSet
import scala.concurrent.duration.DurationInt

class FCSHybridStrategy(val schedulingSystem: SchedulingSystem with WithTaskSpeedEstimate,
                        val config: Configuration) extends Strategy {

    private val measurementTime = config.taskSpeed.flatMap(_.measurement).getOrElse(250.millis)
    private val waitBeforeMeasurementTime = config.taskSpeed.flatMap(_.waitBeforeMeasurement).getOrElse(100.millis)
    private val measurementAttempts = config.taskSpeed.flatMap(_.attempts).getOrElse(1)


    override def execute(nodes: Vector[Node], tasks: Vector[(TaskName, File)]): IO[Unit] = for {
        sTasksWithNode <- tasks.zipWithIndex.view
            .map { case (sTask, idx) => (nodes(idx % nodes.size), sTask) }
            .map { case (node, sTask) => (sTask.pure[IO], schedulingSystem.buildTaskFromTuple(node)(sTask)).tupled}
            .toVector
            .parSequence

        initTasksPartition = sTasksWithNode.groupBy(_._2.node)
        nodeTasksInfos <- initTasksPartition.toVector.parMap(x => (x._1.pure[IO], (nodeTasksInfo _).tupled(x)).tupled)
        workerNodes = initWorkerNodes(nodeTasksInfos)
        action <- workerNodes.toList.parMap(_.execute) >> IO.unit
    } yield action

    private def nodeTasksInfo(node: Node, tasks: Vector[(StrategyTask, Task.Built)]): IO[TasksInfo] = {
        val measurer = new SpeedMeasurer(log.debug(node.id)(_), schedulingSystem, measurementTime, measurementAttempts, waitBeforeMeasurementTime)
        val sTasks = tasks.map(_._1).toSet

        val cwsIo = for {
            createdTasks <- tasks.map(_._2).parMap(schedulingSystem.createTask(_))
            result <- measurer.measureCombinationSpeeds(createdTasks)
        } yield result

        for {
            cws <- cwsIo
            ref <- Ref.of[IO, (Set[StrategyTask], TreeSet[CombinationWithSpeed])]((sTasks, cws))
        } yield ref
    }

    private def initWorkerNodes(nodeTasksInfos: Iterable[(Node, TasksInfo)]): Iterable[WorkerNode] = {
        val allTasksInfos = nodeTasksInfos.map(_._2).toSet
        nodeTasksInfos.map {
            case (node, nodeTasksInfo) => WorkerNode(node, nodeTasksInfo, (allTasksInfos - nodeTasksInfo).toList)
        }
    }

    private case class WorkerNode(node: Node, tasksInfo: TasksInfo, otherTasks: List[TasksInfo]) {
        def execute: IO[Unit] = for {
            _ <- log.info(node.id)("Starting FCS stage.")
            fcs <- fcsStage.withTime
            _ <- log.info(node.id)(s"FCS stage is completed for ${fcs._2}")
            seq <- seqStage.withTime
            _ <- log.info(node.id)(s"SEQ stage is completed for ${seq._2}")
        } yield ()

        def fcsStage: IO[Unit] = for {
            combinationOpt <- fcsStageCombination
            action <- combinationOpt.fold(IO.unit)(fcsStageRunCombination(_) >> fcsStage)
        } yield action

        private val fcsStageCombination: IO[Option[Combination]] = tasksInfo.modify { case ti @ (sTasks, cwsSet) =>
            cwsSet.maxOption match {
                case Some(cws) =>
                    val taskNames = cws.combination.map(_.title)
                    val updatedSTasks = sTasks.filter(sTask => !taskNames(sTask._1))
                    ((updatedSTasks, cwsSet - cws), Some(cws.combination))
                case None => (ti, None)
            }
        }

        private def fcsStageRunCombination(combination: Combination): IO[Unit] = for {
            _ <- log.debug(node.id)(s"Starting combination ${combination.map(_.title).mkString(",")}")
            _ <- combination.genParMap(_.toList)(schedulingSystem.saveResumeTask)
            _ <- raceCallbacks(combination.map(fcsStageTaskCallback).toList)
            _ <- fcsStageAfterCombinationComplete(combination)
        } yield ()

        private def fcsStageAfterCombinationComplete(combination: Combination): IO[Unit] = for {
            pausedTasks <- combination.genParMap(_.toList)(schedulingSystem.savePauseTask)
            _ <- log.debug(node.id)(s"Combination ${combination.map(_.title).mkString(",")} is paused.")
            taskWithIsRunning <- pausedTasks.parMap { task => (task.pure[IO], schedulingSystem.isRunning(task)).tupled }
            completedTasks = taskWithIsRunning.collect { case (task, false) => task }
            _ <- tasksInfo.update { case (sTasks, cwsSet) =>
                val completedTasksSet = completedTasks.toSet
                val updatedCwsSet = cwsSet.filter(cws => !cws.combination.exists(completedTasksSet))
                (sTasks, updatedCwsSet)
            }
        } yield ()

        private def fcsStageTaskCallback(task: Task.Created): IO[Unit] =
            schedulingSystem.waitForTask(task) >> log.debug(node.id)(s"Task ${task.title} is completed.")

        private def raceCallbacks(callbacks: Seq[IO[Unit]]): IO[Unit] = callbacks.foldLeft(IO.never[Unit]) {
            case (io, curCallback) => io.race(curCallback) >> IO.unit
        }

        def seqStage: IO[Unit] = for {
            taskOpt <- seqStateTask()
            action <- taskOpt match {
                case Some(sTask) => for {
                    builtTask <- schedulingSystem.buildTaskFromTuple(node)(sTask)
                    createdTask <- schedulingSystem.createTask(builtTask)
                    startedTask <- schedulingSystem.startTask(createdTask)
                    _ <- log.debug(node.id)(s"Task ${startedTask.title} has been started sequentially: $startedTask")
                    action <- schedulingSystem.waitForTask(startedTask) >> seqStage
                } yield action
                case None => IO.unit
            }
        } yield action

        private def seqStateTask(rem: List[TasksInfo] = otherTasks): IO[Option[StrategyTask]] = rem match {
            case Nil => None.pure[IO]
            case ti :: tis => for {
                taskOpt <- taskFromInfo(ti)
                result <- taskOpt.fold(seqStateTask(tis))(_.some.pure[IO])
            } yield result
        }

        private def taskFromInfo(tasksInfo: TasksInfo): IO[Option[StrategyTask]] =
            tasksInfo.modify { case ti @ (sTasks, cwsSet) =>
                sTasks.map(sTask => (
                    sTask,
                    // поиск скорости задачи sTask в комбинации, состоящей только из нее
                    cwsSet.find(cws => cws.combination.size == 1 && cws.combination.head.title == sTask._1).map(_.speed)
                // выбираем для выполнения задачу с минимальной скоростью на узле
                // TODO мб лучше взять макс. скорость?
                )).collect { case (sTask, Some(speed)) => (sTask, speed) }.minByOption(_._2) match {
                    case Some((sTask, _)) =>
                        val taskName = sTask._1
                        // удаляем из множества комбинаций на узле те комбинации, которые содержат выбранную задачу
                        val updatedCwsSet = cwsSet.filter(cws => !cws.combination.exists(_.title == taskName))
                        // удаляем из множества StrategyTask этого узла выбранную задачу
                        ((sTasks - sTask, updatedCwsSet), Some(sTask))
                    case None => (ti, None)
                }
            }
    }
}

object FCSHybridStrategy {
    type TasksInfo = Ref[IO, (Set[StrategyTask], TreeSet[CombinationWithSpeed])]

    def apply(schedulingSystem: SchedulingSystem with WithTaskSpeedEstimate, config: Configuration): FCSHybridStrategy =
        new FCSHybridStrategy(schedulingSystem, config)
}