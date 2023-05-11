package ru.skelantros.coscheduler.main.strategy

import cats.effect.{IO, Ref}
import cats.implicits._
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.main.system.SchedulingSystem
import ru.skelantros.coscheduler.model.Node

/**
 * Последовательная стратегия - запуск всех задач по очереди монопольно на каждом узле.
 * Как только задача на каком-либо узле завершает выполнение, на узле запускается следующая задача из списка.
 * Если список пуст, на узле ничего больше не запускается.
 *
 * Данную "тривиальную" стратегию будем использовать для сравнения с кошедулинг-стратегиями.
 */
class SequentialStrategy(val schedulingSystem: SchedulingSystem, val config: Configuration) extends Strategy {
    private case class SingleNodeWorker(tasksRef: Ref[IO, List[StrategyTask]])(node: Node) {
        def execute: IO[Unit] = for {
            taskOpt <- tasksRef.modify {
                case task :: tasks => (tasks, Some(task))
                case _ => (Nil, None)
            }
            action <- taskOpt match {
                case Some(task) =>
                    schedulingSystem.buildTaskFromTuple(node)(task)
                        .flatMap(schedulingSystem.createTask(_))
                        .flatMap(schedulingSystem.startTask)
                        .flatMap(schedulingSystem.waitForTask) >> log.debug(node.id)(s"$task executed.") >> execute
                case None => IO.unit
            }
        } yield action
    }

    override def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Unit] =
        for {
            tasksRef <- Ref[IO].of(tasks.toList)
            workers = nodes.map(SingleNodeWorker(tasksRef))
            action <- workers.map(_.execute).parSequence >> IO.unit
        } yield action
}

object SequentialStrategy {
    def apply(schedulingSystem: SchedulingSystem, config: Configuration): SequentialStrategy =
        new SequentialStrategy(schedulingSystem, config)
}