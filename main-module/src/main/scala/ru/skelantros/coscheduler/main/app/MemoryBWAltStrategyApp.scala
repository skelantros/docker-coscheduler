package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{MemoryBWAltStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, SchedulingSystem, WithMmbwmon}

object MemoryBWAltStrategyApp extends AbstractMainApp[SchedulingSystem with WithMmbwmon] {
    override val initStrategy: (SchedulingSystem with WithMmbwmon, Configuration) => Strategy =
        MemoryBWAltStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithMmbwmon =
        HttpSchedulingSystem.withLogging(config)
}
