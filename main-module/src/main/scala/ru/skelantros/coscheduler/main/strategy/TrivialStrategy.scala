package ru.skelantros.coscheduler.main.strategy

import cats.effect.IO
import cats.implicits.catsSyntaxParallelSequence1
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.TaskName
import ru.skelantros.coscheduler.main.system.SchedulingSystem

import java.io.File

class TrivialStrategy(schedulingSystem: SchedulingSystem, config: Configuration) extends Strategy {
    override def execute(tasks: Vector[(TaskName, File)]): IO[Unit] = {
        val nodes = config.nodes.toVector
        val ioBuiltTasks = tasks.zipWithIndex.map {
            case ((taskName, imageDir), idx) =>
                for {
                    builtTask <- schedulingSystem.buildTaskFromDir(nodes(idx % nodes.size))(imageDir, Some(taskName))
                    createdTask <- schedulingSystem.createTask(builtTask)
                    startedTask <- schedulingSystem.startTask(createdTask)
                    _ <- schedulingSystem.waitForTask(startedTask)
                } yield ()
        }

        ioBuiltTasks.parSequence >> IO.unit
    }
}

object TrivialStrategy {
    def apply(schedulingSystem: SchedulingSystem, config: Configuration): TrivialStrategy =
        new TrivialStrategy(schedulingSystem, config)
}