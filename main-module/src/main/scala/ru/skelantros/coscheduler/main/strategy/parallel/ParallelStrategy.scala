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

    protected def singleNodeExecute(node: Node, tasks: Vector[Task.Built]): IO[Strategy.PartialInfo]

    override def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Strategy.PartialInfo] = for {
        sTasksWithNode <- tasks.zipWithIndex.view
            .map { case (sTask, idx) => (nodes(idx % nodes.size), sTask) }
            .map(Function.uncurried(schedulingSystem.buildTaskFromTuple _).tupled)
            .toVector
            .parSequence
        partialInfos <- sTasksWithNode.groupBy(_.node)
            .map((singleNodeExecute _).tupled)
            .toList
            .parSequence
    } yield Strategy.PartialInfo(partialInfos.map(_.preStageTime).max)
}
