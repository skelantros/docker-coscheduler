package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy
import ru.skelantros.coscheduler.main.strategy.parallel.FCStrategy
import ru.skelantros.coscheduler.main.system._

object FCApp extends AbstractMainApp[SchedulingSystem with WithTaskSpeedEstimate] {
    override val initStrategy: (SchedulingSystem with WithTaskSpeedEstimate, Configuration) => Strategy = FCStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithTaskSpeedEstimate =
        HttpSchedulingSystem.withLogging(config)
}
