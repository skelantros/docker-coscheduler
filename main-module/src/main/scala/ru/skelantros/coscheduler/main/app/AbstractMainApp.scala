package ru.skelantros.coscheduler.main.app

import cats.effect.{ExitCode, IO, IOApp}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy
import ru.skelantros.coscheduler.main.system.SchedulingSystem

trait AbstractMainApp[S <: SchedulingSystem] extends IOApp {
    val initStrategy: (S, Configuration) => Strategy

    private def loadConfiguration(args: List[String]): Option[Configuration] =
        args.headOption.fold(ConfigSource.default)(ConfigSource.file).load[Configuration].toOption

    def schedulingSystem(config: Configuration): S

    override def run(args: List[String]): IO[ExitCode] = {
        val strategyWithTasks = for {
            config <- loadConfiguration(args)
            tasks = config.tasks
        } yield (initStrategy(schedulingSystem(config), config), tasks)

        strategyWithTasks.fold(
            IO.pure(ExitCode.Error)
        ) { case (strategy, tasks) => strategy.executeWithInit(tasks) >> IO.pure(ExitCode.Success) }
    }
}
