package ru.skelantros.coscheduler.main.strategy

import cats.effect.{IO, Ref}
import cats.implicits._
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.implicits._
import ru.skelantros.coscheduler.main.strategy.MemoryBWAltStrategy._
import ru.skelantros.coscheduler.main.strategy.Strategy._
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithMmbwmon}
import ru.skelantros.coscheduler.main.utils.SemaphoreResource
import ru.skelantros.coscheduler.model.{CpuSet, Node, Task}

import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt

class MemoryBWAltStrategy(val schedulingSystem: SchedulingSystem with WithMmbwmon, val config: Configuration, mmbwmonCores: Option[Int] = None) extends Strategy {
    private val waitBeforeMmbwmon = config.mmbwmon.flatMap(_.waitBeforeMeasurement).getOrElse(250.millis)
    private val mmbwmonAttempts = config.mmbwmon.flatMap(_.attempts).getOrElse(5)
    private val threshold = config.mmbwmon.flatMap(_.threshold).getOrElse(0.9)
    private val delay = config.mmbwmon.flatMap(_.retryDelay).getOrElse(500.millis)

    private val coresCount = (mmbwmonCores orElse config.mmbwmon.map(_.coresCount)).get
    private val mmbwmonCpuSet = CpuSet(0, coresCount)
    private def taskBenchCpuSet(node: Node): CpuSet = CpuSet(coresCount, node.cores - coresCount)

    private def preStage(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Vector[WorkerNode]] = for {
        sharedTasksRef <- Ref.of[IO, SharedTasks](tasks.toSet)
        nodesWithBwResults <- nodes.map(benchmarkAllTasks(tasks)).parSequence
        workerNodes <- nodesWithBwResults.parMap((WorkerNode(sharedTasksRef) _).tupled)
    } yield workerNodes

    override def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Strategy.PartialInfo] = for {
        preStageWithTime <- preStage(nodes, tasks).withTime
        (workerNodes, preStageTime) = preStageWithTime
        tasksToWait <- workerNodes.parMap(_.execute)
        _ <- tasksToWait.flatten.parMap(schedulingSystem.waitForTask)
    } yield Strategy.PartialInfo(Some(preStageTime))

    private def benchmarkAllTasks(tasks: Vector[StrategyTask])(node: Node): IO[(Node, Vector[NodeTask])] = for {
        createdTasks <- tasks.parMap { sTask =>
            (
                schedulingSystem.buildTaskFromTuple(node)(sTask)
                .flatMap(schedulingSystem.createTask(_, Some(taskBenchCpuSet(node)))),
                sTask.pure[IO]
            ).tupled
        }
        nodeTasks <- createdTasks.map((benchmarkTask(node) _).tupled).sequence
        _ <- log.debug(node.id)(s"Measurements for all tasks are:\n${nodeTasks.sortBy(_.speed).mkString("\n")}")
    } yield (node, nodeTasks)

    private def benchmarkTask(node: Node)(createdTask: Task.Created, sTask: StrategyTask): IO[NodeTask] = for {
        startedTask <- schedulingSystem.startTask(createdTask)
        benchmarkResult <- schedulingSystem.avgMmbwmon(node, mmbwmonCpuSet)(mmbwmonAttempts).delayBy(waitBeforeMmbwmon)
        pausedTask <- schedulingSystem.savePauseTask(startedTask)
        // после бенчмарка выдаем задаче в пользование все ядра. хуже не станет
        updatedCpusTask <- schedulingSystem.updateCpus(pausedTask, CpuSet(0, node.cores))
        _ <- log.debug(node.id)(s"${createdTask.title}: $benchmarkResult")
    } yield NodeTask(sTask, benchmarkResult, updatedCpusTask)

    private class WorkerNode(node: Node, nodeTasks: List[NodeTask], sharedTasks: Ref[IO, SharedTasks], bwAndWaiting: SemaphoreResource[(Ref[IO, Double], Ref[IO, Boolean]), IO]) {
        def execute: IO[Set[Task.Created]] = go(nodeTasks, Set.empty)

        private def go(nodeTasks: List[NodeTask], tasks: Set[Task.Created]): IO[Set[Task.Created]] = for {
            findTasksRes <- findTasks(nodeTasks)
            action <- findTasksRes match {
                case x @ TasksFound(foundTasks, updatedNodeTasks) =>
                    log.debug(node.id)(x.toString) >>
                    foundTasks.parMap(t => schedulingSystem.saveResumeTask(t.task)) >>
                    runTasksCallback(foundTasks) >>
                    go(updatedNodeTasks, foundTasks.foldLeft(tasks)(_ + _.task))
                case x @ NoMoreTasks(foundTasks) =>
                    log.debug(node.id)(x.toString) >>
                    foundTasks.parMap(t => schedulingSystem.saveResumeTask(t.task)) >>
                    runTasksCallback(foundTasks) >>
                    foundTasks.foldLeft(tasks)(_ + _.task).pure[IO]
                case TasksNotFound => go(nodeTasks, tasks).delayBy(delay)
            }
        } yield action

        private def findTasks(nodeTasks: List[NodeTask]): IO[FindTasksResult] = bwAndWaiting.getAndComputeF { case (bwRef, waitingRef) =>
            for {
                bw <- bwRef.get
                waiting <- waitingRef.get
                result <-
                    if(waiting) TasksNotFound.pure[IO]
                    else sharedTasks.modify { sts =>
                        suitableTasks(nodeTasks, sts, bw) match {
                            case (chosenTasks, Some(updatedNodeTasks)) =>
                                (chosenTasks.foldLeft(sts)(_ - _.sTask), waitingRef.set(true) >> TasksFound(chosenTasks, updatedNodeTasks).pure[IO])
                            case (chosenTasks, None) =>
                                // т.к. всегда sts in nodeTasks, то этот кейс возможен т. и т., когда задач больше не осталось
                                (Set.empty, NoMoreTasks(chosenTasks).pure[IO])
                        }
                    }.flatten
            } yield result
        }

        /**
         * первый элемент кортежа (список выбранных задач) отсортирован в обратном порядке, второй - просто отсортирован
         */
        @tailrec
        private def suitableTasks(nodeTasks: List[NodeTask], sts: SharedTasks, bw: Double, cur: List[NodeTask] = List.empty): (List[NodeTask], Option[List[NodeTask]]) = nodeTasks match {
            case t :: ts if !sts(t.sTask) => suitableTasks(ts, sts, bw, cur)
            case t :: ts if bw == 0 || t.speed + bw <= threshold => suitableTasks(ts, sts, bw + t.speed, t :: cur)
            case _ :: _ => (cur, Some(nodeTasks))
            case Nil => (cur, None)
        }

        private def runTasksCallback(nodeTasks: List[NodeTask]): IO[Unit] = {
            lazy val tasksTotalBw = nodeTasks.sumBy(_.speed)

            val callbackStart = bwAndWaiting.getAndComputeF { case (bwRef, _) =>
                for {
                    _ <- bwRef.update(_ + tasksTotalBw)
                    totalBw <- bwRef.get
                    _ <- log.debug(node.id)(s"Tasks ${nodeTasks.map(_.task.title).mkString(",")} have been started. totalBw = $totalBw")
                } yield ()
            }

            val singleCallbackEnd = (task: NodeTask) =>
                schedulingSystem.waitForTask(task.task) >>
                bwAndWaiting.getAndComputeF { case (bwRef, waitingRef) =>
                    for {
                        _ <- bwRef.update(_ - task.speed)
                        _ <- waitingRef.set(false)
                        totalBw <- bwRef.get
                        _ <- log.debug(node.id)(s"Task ${task.task.title} has been completed. totalBw = $totalBw")
                    } yield ()
                }

            val callbacksEnd = nodeTasks.parMap(singleCallbackEnd(_).start)

            callbackStart >> callbacksEnd >> IO.unit
        }
    }

    private object WorkerNode {
        def apply(sharedTasks: Ref[IO, SharedTasks])(node: Node, nodeTasks: Iterable[NodeTask]): IO[WorkerNode] = for {
            bwRef <- Ref.of[IO, Double](0d)
            waitingRef <- Ref.of[IO, Boolean](false)
            resource <- SemaphoreResource[IO].from((bwRef, waitingRef), 1)
        } yield new WorkerNode(node, nodeTasks.toList.sorted, sharedTasks, resource)
    }
}

object MemoryBWAltStrategy {
    def apply(schedulingSystem: SchedulingSystem with WithMmbwmon, config: Configuration): MemoryBWAltStrategy =
        new MemoryBWAltStrategy(schedulingSystem, config)

    def apply(schedulingSystem: SchedulingSystem with WithMmbwmon, config: Configuration, mmbwmonCores: Int): MemoryBWAltStrategy =
        new MemoryBWAltStrategy(schedulingSystem, config, Some(mmbwmonCores))

    private type SharedTasks = Set[StrategyTask]
}

private sealed trait FindTasksResult extends Product
private case class TasksFound(tasksToStart: List[NodeTask], updatedNodeTasks: List[NodeTask]) extends FindTasksResult
private case class NoMoreTasks(tasks: List[NodeTask]) extends FindTasksResult
private case object TasksNotFound extends FindTasksResult

private case class NodeTask(sTask: StrategyTask, speed: Double, task: Task.Created)

private object NodeTask {
    implicit val ordering: Ordering[NodeTask] = Ordering.by[NodeTask, Double](_.speed)
}