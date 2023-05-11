package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{MemoryBWAltStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, SchedulingSystem, WithRamBenchmark}

object MemoryBWAltStrategyApp extends AbstractMainApp[SchedulingSystem with WithRamBenchmark] {
    override val initStrategy: (SchedulingSystem with WithRamBenchmark, Configuration) => Strategy =
        MemoryBWAltStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithRamBenchmark =
        HttpSchedulingSystem.withLogging(config)
}
