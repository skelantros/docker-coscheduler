package ru.skelantros.coscheduler.main.strategy

import cats.effect.{IO, Ref}
import cats.implicits.catsSyntaxParallelSequence1
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.MemoryBWStrategy.{StrategyTaskInfo, atomicRefAction}
import ru.skelantros.coscheduler.main.strategy.Strategy.{StrategyTask, TaskName}
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithRamBenchmark}
import ru.skelantros.coscheduler.model.{CpuSet, Node, Task}

import java.io.File
import scala.concurrent.duration.DurationInt

class MemoryBWStrategy(val schedulingSystem: SchedulingSystem with WithRamBenchmark,
                       val config: Configuration) extends Strategy {

    private case class NodeWorker(tasksRef: Ref[IO, Set[StrategyTaskInfo]])(node: Node) {

        // отменяет выполнение задачи и возвращает ее в общую очередь без отметки выполнения и с фейлед для данного узла
        private def migrateTask(taskInfo: StrategyTaskInfo, task: Task.Created): IO[Unit] = for {
            _ <- schedulingSystem.stopTask(task)
            _ <- tasksRef.update { tasks =>
                val updatedTaskInfo = taskInfo.copy(executorNode = None, failedNodes = taskInfo.failedNodes + this.node)
                tasks - taskInfo + updatedTaskInfo
            }
        } yield ()

        private def stayTaskCallback(task: Task.Created, tasksCountRef: Ref[IO, Int]) =
            schedulingSystem.waitForTask(task) >>
                tasksRef.update(_.map(taskInfo => taskInfo.copy(failedNodes = taskInfo.failedNodes - node))) >>
                tasksCountRef.update(_ - 1)

        /**
         * Действия в случае, если принято решение оставить задачу на узле.
         * 1) Описание задачи удаляется из очереди.
         * 2) Обновляется счетчик задач на узле.
         * 3) Вызывается коллбек, который ожидает выполнения задачи, а затем обновляет очередь (убирает метку failedNodes) и уменьшает счетчик
         */
        private def stayTask(task: Task.Created, taskInfo: StrategyTaskInfo, tasksCountRef: Ref[IO, Int]): IO[Task.Created] = for {
            _ <- tasksRef.update(_ - taskInfo)
            _ <- tasksCountRef.update(_ + 1)
            // potentially unsafe
            _ <- stayTaskCallback(task, tasksCountRef).start
        } yield task

        // +
        private def runTask(taskInfo: StrategyTaskInfo, tasksCountRef: Ref[IO, Int]): IO[Option[Task.Created]] = for {
            builtTask <- schedulingSystem.buildTaskFromTuple(node)(taskInfo.task)
            createdTask <- schedulingSystem.createTask(builtTask, Some(CpuSet(1, node.cores - 1)))
            startedTask <- schedulingSystem.startTask(createdTask)
            benchmarkResult <- schedulingSystem.ramBenchmark(node)
            action <- atomicRefAction(tasksCountRef) { tasksCount =>
                if (tasksCount > 0 && config.bwThreshold.exists(_ < benchmarkResult)) migrateTask(taskInfo, startedTask) >> IO.pure(None)
                else stayTask(startedTask, taskInfo, tasksCountRef).map(Some(_))
            }
        } yield action

        private def runTaskAction(taskInfo: StrategyTaskInfo, tasksCountRef: Ref[IO, Int], runTasks: Set[Task.Created]): IO[Set[Task.Created]] = for {
            runTaskOpt <- runTask(taskInfo, tasksCountRef)
            newRunTasks = runTaskOpt.fold(runTasks)(runTasks + _)
            // Продолжаем планирование после запуска/незапуска задачи
            action <- go(tasksCountRef, newRunTasks)
        } yield action

        private def go(activeTasksCountRef: Ref[IO, Int], runTasks: Set[Task.Created]): IO[Set[Task.Created]] = for {
            actionOpt <- tasksRef.modify { tasks =>
                // Поиск невыполнямемой задачи без метки фейлед для данного узла
                tasks.find(task => task.executorNode.isEmpty && !task.failedNodes(node)) match {
                    // Если нашлась задача - запускаем ее. В очередь возвращаем задачу с отметкой исполнения
                    case Some(taskInfo) =>
                        val newTaskInfo = taskInfo.copy(executorNode = Some(node))
                        (tasks - taskInfo + newTaskInfo, Some(runTaskAction(newTaskInfo, activeTasksCountRef, runTasks)))
                    // Если очередь в принципе пустая - останавливаем планирование на узле
                    case None if tasks.isEmpty => (tasks, None)
                    // Если очередь не пустая И задача не нашлась - перезапускаем планирование с делеем
                    case None => (tasks, Some(go(activeTasksCountRef, runTasks).delayBy(1.seconds)))
                }
            }

            // Если на предыдущем шаге никакое действие не было решено выполнять, просто возвращаем список успешно запущенных задач
            action <- actionOpt.getOrElse(IO.pure(runTasks))
        } yield action

        // планирование + возврат множества задач, которые были успешно запущены на узле
        def start: IO[Set[Task.Created]] = for {
            tasksCountRef <- Ref.of[IO, Int](0)
            action <- go(tasksCountRef, Set.empty)
        } yield action
    }

    override def execute(tasks: Vector[(TaskName, File)]): IO[Unit] = for {
        nodes <- this.nodes
        tasksRef <- Ref.of[IO, Set[StrategyTaskInfo]](tasks.map(StrategyTaskInfo(_)).toSet)
        tasksToWait <- nodes.map(NodeWorker(tasksRef)).map(_.start).parSequence
        action <- tasksToWait.flatten.map(schedulingSystem.waitForTask).parSequence >> IO.unit
    } yield action
}

object MemoryBWStrategy {
    private case class StrategyTaskInfo(task: StrategyTask,
                                        failedNodes: Set[Node] = Set(),
                                        executorNode: Option[Node] = None)


    private def atomicRefAction[A, O](ref: Ref[IO, A])(f: A => IO[O]): IO[O] =
        ref.modify { refValue =>
            (refValue, f(refValue))
        }.flatten
}