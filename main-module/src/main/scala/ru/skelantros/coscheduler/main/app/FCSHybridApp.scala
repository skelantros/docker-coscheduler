package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{FCSHybridStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, SchedulingSystem, WithTaskSpeedEstimate}

object FCSHybridApp extends AbstractMainApp[SchedulingSystem with WithTaskSpeedEstimate] {
    override val initStrategy: (SchedulingSystem with WithTaskSpeedEstimate, Configuration) => Strategy =
        FCSHybridStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithTaskSpeedEstimate =
        HttpSchedulingSystem.withLogging(config)
}
