package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{MemoryBWStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, SchedulingSystem, WithRamBenchmark}

object MemoryBWMainApp extends AbstractMainApp[SchedulingSystem with WithRamBenchmark] {
    override val initStrategy: (SchedulingSystem with WithRamBenchmark, Configuration) => Strategy = MemoryBWStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithRamBenchmark =
        HttpSchedulingSystem.withLogging(config)
}
