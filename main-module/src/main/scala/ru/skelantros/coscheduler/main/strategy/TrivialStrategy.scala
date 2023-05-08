package ru.skelantros.coscheduler.main.strategy

import cats.effect.IO
import cats.implicits.catsSyntaxParallelSequence1
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.main.system.SchedulingSystem
import ru.skelantros.coscheduler.model.Node

class TrivialStrategy(val schedulingSystem: SchedulingSystem, val config: Configuration) extends Strategy {
    override def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Unit] = {
        for {
            result <- tasks.zipWithIndex.map {
                case (taskWithName, idx) =>
                    for {
                        builtTask <- schedulingSystem.buildTaskFromTuple(nodes(idx % nodes.size))(taskWithName)
                        createdTask <- schedulingSystem.createTask(builtTask)
                        startedTask <- schedulingSystem.startTask(createdTask)
                        _ <- schedulingSystem.waitForTask(startedTask)
                    } yield ()
            }.parSequence >> IO.unit
        } yield  result
    }
}

object TrivialStrategy {
    def apply(schedulingSystem: SchedulingSystem, config: Configuration): TrivialStrategy =
        new TrivialStrategy(schedulingSystem, config)
}