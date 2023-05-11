package ru.skelantros.coscheduler.main.strategy

import cats.effect.{IO, Ref}
import cats.implicits._
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.implicits._
import ru.skelantros.coscheduler.main.strategy.MemoryBWAltStrategy._
import ru.skelantros.coscheduler.main.strategy.Strategy._
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithRamBenchmark}
import ru.skelantros.coscheduler.main.utils.SemaphoreResource
import ru.skelantros.coscheduler.model.{CpuSet, Node, Task}

import scala.collection.immutable.TreeSet
import scala.concurrent.duration.DurationInt

class MemoryBWAltStrategy(val schedulingSystem: SchedulingSystem with WithRamBenchmark, val config: Configuration) extends Strategy {
    private val waitBeforeMmbwmon = config.mmbwmon.flatMap(_.waitBeforeMeasurement).getOrElse(250.millis)
    private val mmbwmonAttempts = config.mmbwmon.flatMap(_.attempts).getOrElse(5)
    private val threshold = config.mmbwmon.flatMap(_.threshold).getOrElse(0.9)
    private val delay = config.mmbwmon.flatMap(_.retryDelay).getOrElse(500.millis)

    override def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Unit] = for {
        sharedTasksRef <- Ref.of[IO, SharedTasks](tasks.toSet)
        nodesWithBwResults <- nodes.map(benchmarkAllTasks(tasks)).parSequence
        workerNodes <- nodesWithBwResults.parMap((WorkerNode(sharedTasksRef) _).tupled)
        tasksToWait <- workerNodes.parMap(_.execute)
        action <- tasksToWait.flatten.parMap(schedulingSystem.waitForTask) >> IO.unit
    } yield action

    private def benchmarkAllTasks(tasks: Vector[StrategyTask])(node: Node): IO[(Node, Vector[NodeTask])] = for {
        createdTasks <- tasks.parMap { sTask =>
            (
                schedulingSystem.buildTaskFromTuple(node)(sTask)
                .flatMap(schedulingSystem.createTask(_, Some(CpuSet(1, node.cores - 1)))),
                sTask.pure[IO]
            ).tupled
        }
        nodeTasks <- createdTasks.map((benchmarkTask(node) _).tupled).sequence
    } yield (node, nodeTasks)

    private def benchmarkTask(node: Node)(createdTask: Task.Created, sTask: StrategyTask): IO[NodeTask] = for {
        startedTask <- schedulingSystem.startTask(createdTask)
        benchmarkResult <- schedulingSystem.avgRamBenchmark(node)(mmbwmonAttempts).delayBy(waitBeforeMmbwmon)
        pausedTask <- schedulingSystem.savePauseTask(startedTask)
    } yield NodeTask(sTask, benchmarkResult, pausedTask)

    private class WorkerNode(node: Node, nodeTasks: TreeSet[NodeTask], sharedTasks: Ref[IO, SharedTasks], bwAndWaiting: SemaphoreResource[(Ref[IO, Double], Ref[IO, Boolean]), IO]) {
        def execute: IO[Set[Task.Created]] = go(nodeTasks, Set.empty)

        private def go(nodeTasks: TreeSet[NodeTask], tasks: Set[Task.Created]): IO[Set[Task.Created]] = for {
            findTaskRes <- findTask(nodeTasks)
            action <- findTaskRes match {
                case TaskFound(task) =>
                    schedulingSystem.saveResumeTask(task.task) >> runTaskCallback(task).start >> go(nodeTasks - task, tasks + task.task)
                case NoMoreTasks => tasks.pure[IO]
                case NotFound => go(nodeTasks, tasks).delayBy(delay)
            }
        } yield action

        private def findTask(nodeTasks: TreeSet[NodeTask]): IO[FindTaskResult] = bwAndWaiting.getAndComputeF { case (bwRef, waitingRef) =>
            for {
                bw <- bwRef.get
                waiting <- waitingRef.get
                result <-
                    if (waiting) NotFound.pure[IO]
                    else sharedTasks.modify { sts =>
                        if (sts.isEmpty) (sts, IO.pure(NoMoreTasks))
                        else nodeTasks.find(nt => sts(nt.sTask)) match {
                            case Some(nodeTask) if bw == 0 || bw + nodeTask.speed <= threshold =>
                                (sts - nodeTask.sTask, IO.pure(TaskFound(nodeTask)))
                            case _ =>
                                (sts, IO.pure(NotFound) <* waitingRef.set(true))
                        }
                    }.flatten
            } yield result
        }

        private def runTaskCallback(task: NodeTask): IO[Unit] = {
            val callbackStart = bwAndWaiting.getAndComputeF { case (bwRef, _) =>
                for {
                    _ <- bwRef.update(_ + task.speed)
                    totalBw <- bwRef.get
                    _ <- log.debug(node.id)(s"Task $task has been started. totalBw = $totalBw")
                } yield ()
            }

            val callbackEnd = bwAndWaiting.getAndComputeF { case (bwRef, waitingRef) =>
                for {
                    _ <- bwRef.update(_ - task.speed)
                    _ <- waitingRef.set(false)
                    totalBw <- bwRef.get
                    _ <- log.debug(node.id)(s"Task $task has been completed. totalBw = $totalBw")
                } yield ()
            }

            callbackStart >> schedulingSystem.waitForTask(task.task) >> callbackEnd
        }
    }

    private object WorkerNode {
        def apply(sharedTasks: Ref[IO, SharedTasks])(node: Node, nodeTasks: Iterable[NodeTask]): IO[WorkerNode] = for {
            bwRef <- Ref.of[IO, Double](0d)
            waitingRef <- Ref.of[IO, Boolean](false)
            resource <- SemaphoreResource[IO].from((bwRef, waitingRef), 1)
        } yield new WorkerNode(node, NodeTask.setFrom(nodeTasks), sharedTasks, resource)
    }
}

object MemoryBWAltStrategy {
    def apply(schedulingSystem: SchedulingSystem with WithRamBenchmark, config: Configuration): MemoryBWAltStrategy =
        new MemoryBWAltStrategy(schedulingSystem, config)

    private type SharedTasks = Set[StrategyTask]
}

private sealed trait FindTaskResult
private case class TaskFound(task: NodeTask) extends FindTaskResult
private case object NoMoreTasks extends FindTaskResult
private case object NotFound extends FindTaskResult

private case class NodeTask(sTask: StrategyTask, speed: Double, task: Task.Created)

private object NodeTask {
    private val ordering = Ordering.by[NodeTask, Double](_.speed).reverse
    def setFrom(coll: Iterable[NodeTask]): TreeSet[NodeTask] = TreeSet.from(coll)(ordering)
}