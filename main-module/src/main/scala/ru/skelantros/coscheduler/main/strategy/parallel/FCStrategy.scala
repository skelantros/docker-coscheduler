package ru.skelantros.coscheduler.main.strategy.parallel

import cats.effect.IO
import cats.implicits._
import ru.skelantros.coscheduler.logging.DefaultLogger
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.implicits._
import ru.skelantros.coscheduler.main.strategy.combination._
import ru.skelantros.coscheduler.main.system.{SchedulingSystem, WithTaskSpeedEstimate}
import ru.skelantros.coscheduler.model.{Node, Task}

import scala.collection.immutable.TreeSet
import scala.concurrent.duration.DurationInt

class FCStrategy(val schedulingSystem: SchedulingSystem with WithTaskSpeedEstimate,
                 val config: Configuration) extends ParallelStrategy with DefaultLogger {

    private val measurementTime = config.taskSpeed.flatMap(_.measurement).getOrElse(250.millis)
    private val waitBeforeMeasurementTime = config.taskSpeed.flatMap(_.waitBeforeMeasurement).getOrElse(100.millis)
    private val measurementAttempts = config.taskSpeed.flatMap(_.attempts).getOrElse(1)

    override protected def singleNodeExecute(node: Node, tasks: Vector[Task.Built]): IO[Unit] =
        NodeWorker(node).execute(tasks)

    private case class NodeWorker(node: Node) {
        private val measurer = new SpeedMeasurer(log.debug(node.id)(_), schedulingSystem, measurementTime, measurementAttempts, waitBeforeMeasurementTime)

        private def raceCallbacks(callbacks: Seq[IO[Unit]]): IO[Unit] = callbacks.foldLeft(IO.never[Unit]) {
            case (io, curCallback) => io.race(curCallback) >> IO.unit
        }

        private def runUntilTaskCompletion(combination: Combination): IO[Unit] = for {
            _ <- log.debug(node.id)(s"Running combination $combination")
            runTasks <- combination.genParMap(_.toList)(schedulingSystem.saveResumeTask)
            callbacks = runTasks.map(schedulingSystem.waitForTask).map(_ >> IO.unit)
            action <- raceCallbacks(callbacks)
        } yield action

        private def goContinue(tasks: Set[Task.Created], combination: Combination, combinations: TreeSet[CombinationWithSpeed]): IO[Unit] = for {
            _ <- runUntilTaskCompletion(combination)
            pausedTasks <- tasks.genParMap(_.toList)(schedulingSystem.savePauseTask)
            tasksWithRunning <- pausedTasks.parMap(task => (task.pure[IO], schedulingSystem.isRunning(task)).tupled)
            completedTasks = tasksWithRunning.collect {
                case (task, false) => task
            }.toSet
            combinationsWithoutCompTasks = combinations.filterNot(_.combination.exists(completedTasks))
            action <- go(pausedTasks.toSet diff completedTasks, combinationsWithoutCompTasks)
        } yield action

        private def go(tasks: Set[Task.Created], combinations: TreeSet[CombinationWithSpeed]): IO[Unit] = for {
            _ <- log.debug(node.id)(s"Current combinations are:\n${combinations.mkString("\n")}")
            combinationOpt <- IO.pure(combinations.maxOption)
            action <- combinationOpt.fold(IO.unit)(c => goContinue(tasks, c.combination, combinations))
        } yield action

        def execute(builtTasks: Vector[Task.Built]): IO[Unit] = for {
            _ <- IO(require(builtTasks.size < 65 && builtTasks.forall(_.node == node)))
            tasks <- builtTasks.parMap { task =>
                for {
                    create <- schedulingSystem.createTask(task)
                    start <- schedulingSystem.startTask(create)
                    pause <- schedulingSystem.savePauseTask(start)
                } yield pause
            }
            combinations <- measurer.measureCombinationSpeeds(tasks)
            action <- go(tasks.toSet, combinations)
        } yield action
    }
}

object FCStrategy {
    def apply(schedulingSystem: SchedulingSystem with WithTaskSpeedEstimate, config: Configuration): FCStrategy =
        new FCStrategy(schedulingSystem, config)
}