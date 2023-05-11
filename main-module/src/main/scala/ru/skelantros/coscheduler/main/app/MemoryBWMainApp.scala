package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{MemoryBWStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, SchedulingSystem, WithMmbwmon}

object MemoryBWMainApp extends AbstractMainApp[SchedulingSystem with WithMmbwmon] {
    override val initStrategy: (SchedulingSystem with WithMmbwmon, Configuration) => Strategy = MemoryBWStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithMmbwmon =
        HttpSchedulingSystem.withLogging(config)
}
