package ru.skelantros.coscheduler.main.strategy.parallel

import cats.effect.IO
import cats.implicits._
import ru.skelantros.coscheduler.main.strategy.Strategy
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.model.{Node, Task}

/**
 * Общий трейт для стратегий, которые сначала раскидывают равномерно задачи между узлами, а затем
 * планируют исполнение задач на каждом узле параллельно независимо между узлами.
 * Перед основным этапом планирования задачи билдятся на соответствующих узлах параллельно.
 */
trait ParallelStrategy extends Strategy {

    protected def singleNodeExecute(node: Node, tasks: Vector[Task.Built]): IO[Unit]

    override def execute(tasks: Vector[StrategyTask]): IO[Unit] = for {
        nodes <- this.nodes
        sTasksWithNode <- tasks.zipWithIndex.map {
            case (sTask, idx) =>
                val node = nodes(idx % nodes.size)
                (
                    node.pure[IO],
                    schedulingSystem.buildTaskFromTuple(node)(sTask)
                ).tupled
        }.parSequence
        nodesTasks = sTasksWithNode.groupMap(_._1)(_._2)
        action <- nodesTasks.toList.map((singleNodeExecute _).tupled).parSequence >> IO.unit
    } yield action
}
