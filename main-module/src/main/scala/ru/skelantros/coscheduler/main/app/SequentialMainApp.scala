package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{SequentialStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, SchedulingSystem}

object SequentialMainApp extends AbstractMainApp[SchedulingSystem] {
    override val initStrategy: (SchedulingSystem, Configuration) => Strategy = SequentialStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem =
        HttpSchedulingSystem.withLogging(config)
}
