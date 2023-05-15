package ru.skelantros.coscheduler.main.app

import cats.effect.{ExitCode, IO, IOApp}
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy
import ru.skelantros.coscheduler.main.system.SchedulingSystem

trait AbstractMainApp[S <: SchedulingSystem] extends IOApp with WithConfigLoad with DefaultLogger {
    override def loggerConfig: Logger.Config = Logger.Config(debug = false)

    val initStrategy: (S, Configuration) => Strategy

    def schedulingSystem(config: Configuration): S

    override def run(args: List[String]): IO[ExitCode] = {
        val strategyWithTasks = for {
            config <- loadConfiguration(args)
            tasks = config.tasks
        } yield (initStrategy(schedulingSystem(config), config), tasks)

        strategyWithTasks.fold(
            IO.pure(ExitCode.Error)
        ) { case (strategy, tasks) => strategy.executeWithInit(tasks).flatMap(x => log.info("")(x)) >> IO.pure(ExitCode.Success) }
    }
}
