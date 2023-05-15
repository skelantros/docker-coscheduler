package ru.skelantros.coscheduler.main.strategy

import cats.effect.{IO, Ref}
import cats.implicits.catsSyntaxParallelSequence1
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.MemoryBWStrategy.{StrategyTaskInfo, atomicRefAction}
import ru.skelantros.coscheduler.main.strategy.Strategy.{StrategyTask, TaskName}
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithMmbwmon}
import ru.skelantros.coscheduler.model.{CpuSet, Node, Task}

import java.io.File
import scala.concurrent.duration.DurationInt

/**
 * Стратегия на основе пропускной способности ОП.
 *
 * 0) Формируется структура данных с задачами, доступная для всех рабочих узлов. Сейчас она реализована в виде неизменяемого
 * атомарного множества, что может серьезно повлиять на работу системы при больших наборах задач:
 * обновление элемента, реализованное в виде атомарной перезаписи, занимает константное время, но с точки зрения памяти
 * скаловский HashSet может быть очень неэффективен при удалении элементов.
 *
 * После этого инициализируются отдельные планировщики, каждый на свой узел, использующие общую структуру данных.
 * С логической точки зрения, они возвращают множество успешно запущенных задач. Планирование осуществляется на узлах
 * параллельно, в рамках каждого отдельного узла - последовательно.
 *
 * Алгоритм планировщика на отдельном узле:
 *
 * 1) Множество задач просматривается в попытке найти задачу, которая не взята никаким другим узлом в работу И
 * не была ранее неуспешно взята этим узлом в работу в текущей итерации (node in task.failedNodes).
 * Возможны следующие варианты:
 *
 *   а) Задач не осталось => планировщик узла возвращает список успешно запущенных задач и прекращает работу.
 *
 *   б) Задачи есть, но все они не удовлетворяют заданным критериям. Тогда планировщик перезапускается через
 *   bwRetryDelay (задается в конфиге, по умолчанию - 1 секунда).
 *
 *   в) Задача найдена => она отмечается в структуре задач как взятая узлом в работу, и начинается ее запуск.
 *
 * 2) Если задача найдена, планировщик запускает ее на узле (build->create->start) и затем оценивает значение
 * потребления пропускной способности с помощью бенчмарка. В качестве оценки берется среднее значение из bmAttempts
 * замеров (значение из конфига, по умолчанию - 5 раз).
 *
 * 3) Если значение бенчмарка превышает заданный порог (из конфига, по умолчанию - 90%), выполнение задачи на узле
 * останавливается, и ее запись в очереди изменяется: отметка о взятии задачи узлом в работу снимается (таким образом
 * другие узлы смогут взять ее в работу) и она помечается как failedNodes для данного узла.
 *
 *   Исключение: если задача на узле единственная, она не отменяется в любом случае.
 *
 * 4) В противном случае задача остается на узле. При этом она удаляется из общей структуры задач, чтобы другие
 * узлы не смогли ее взять в работу повторно. Счетчик запущенных на узле задач увеличивается на единицу.
 *
 * 5) Запускается неблокирующее ожидание выполнения задачи (на фоне). После окончания выполнения задачи:
 *    1. Счетчик запущенных задач уменьшается.
 *    1. Все метки failedNodes среди оставшихся задач для данного узла снимаются. Это нужно для того, чтобы узел
 *    смог повторно попробовать запустить задачи после уменьшения потребления ОП, вызванного окончанием работы задачи.
 *
 * Основной поток ожидает, пока все задачи будут запущены (т.е. когда все планировщики вернут списки запущенных
 * задач), и затем ожидает выполнения всех задач. Когда все задачи закончат выполнение, программа завершает выполнение.
 */
@deprecated
class MemoryBWStrategy(val schedulingSystem: SchedulingSystem with WithMmbwmon,
                       val config: Configuration) extends Strategy { self =>
    private val bmAttempts = config.mmbwmon.flatMap(_.attempts).getOrElse(5)
    private val bwRetryDelay = config.mmbwmon.flatMap(_.retryDelay).getOrElse(1.seconds)
    private val bwThreshold = config.mmbwmon.flatMap(_.threshold).getOrElse(0.9)

    private case class NodeWorker(tasksRef: Ref[IO, Set[StrategyTaskInfo]])(node: Node) {
        private def log(msg: =>String): IO[Unit] = self.log.debug(node.id)(msg)

        // отменяет выполнение задачи и возвращает ее в общую очередь без отметки выполнения и с фейлед для данного узла
        private def migrateTask(taskInfo: StrategyTaskInfo, task: Task.Created): IO[Unit] = for {
            _ <- schedulingSystem.stopTask(task)
            _ <- tasksRef.update { tasks =>
                val updatedTaskInfo = taskInfo.copy(executorNode = None, failedNodes = taskInfo.failedNodes + this.node)
                tasks - taskInfo + updatedTaskInfo
            }
        } yield ()

        private def stayTaskCallback(task: Task.Created, tasksCountRef: Ref[IO, Int]) =
            log(s"starting stayTaskCallback($task)") >>
            schedulingSystem.waitForTask(task) >>
            tasksRef.update(_.map(taskInfo => taskInfo.copy(failedNodes = taskInfo.failedNodes - node))) >>
            tasksCountRef.update(_ - 1) >>
            log(s"completed stayTaskCallback($task)")

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
            benchmarkResult <- schedulingSystem.avgMmbwmon(node)(bmAttempts)
            action <- atomicRefAction(tasksCountRef) { tasksCount =>
                if (tasksCount > 0 && benchmarkResult > bwThreshold)
                    log(s"task $taskInfo will be stopped and migrated") >> migrateTask(taskInfo, startedTask) >> IO.pure(None)
                else
                    log(s"task $taskInfo will stay on the node") >> stayTask(startedTask, taskInfo, tasksCountRef).map(Some(_))
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
                        (tasks - taskInfo + newTaskInfo, Some(log(s"started to run task $taskInfo") >> runTaskAction(newTaskInfo, activeTasksCountRef, runTasks)))
                    // Если очередь в принципе пустая - останавливаем планирование на узле
                    case None if tasks.isEmpty => (tasks, None)
                    // Если очередь не пустая И задача не нашлась - перезапускаем планирование с делеем
                    case None => (tasks, Some(log(s"waiting for non-executed non-failed tasks...") >> go(activeTasksCountRef, runTasks).delayBy(bwRetryDelay)))
                }
            }

            // Если на предыдущем шаге никакое действие не было решено выполнять, просто возвращаем список успешно запущенных задач
            action <- actionOpt.getOrElse(log("no more tasks left") >> IO.pure(runTasks))
        } yield action

        // планирование + возврат множества задач, которые были успешно запущены на узле
        def start: IO[Set[Task.Created]] = for {
            tasksCountRef <- Ref.of[IO, Int](0)
            action <- go(tasksCountRef, Set.empty)
        } yield action
    }

    override def execute(nodes: Vector[Node], tasks: Vector[(TaskName, File)]): IO[Strategy.PartialInfo] = for {
        tasksRef <- Ref.of[IO, Set[StrategyTaskInfo]](tasks.map(StrategyTaskInfo(_)).toSet)
        tasksToWait <- nodes.map(NodeWorker(tasksRef)).map(_.start).parSequence
        _ <- tasksToWait.flatten.map(schedulingSystem.waitForTask).parSequence
    } yield Strategy.PartialInfo(None)
}

object MemoryBWStrategy {
    private case class StrategyTaskInfo(task: StrategyTask,
                                        failedNodes: Set[Node] = Set(),
                                        executorNode: Option[Node] = None)


    private def atomicRefAction[A, O](ref: Ref[IO, A])(f: A => IO[O]): IO[O] =
        ref.modify { refValue =>
            (refValue, f(refValue))
        }.flatten

    def apply(schedulingSystem: SchedulingSystem with WithMmbwmon, config: Configuration): MemoryBWStrategy =
        new MemoryBWStrategy(schedulingSystem, config)
}