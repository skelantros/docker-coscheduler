package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{MemoryBWStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, LoggingSchedulingSystem, SchedulingSystem, WithRamBenchmark}

object MemoryBWMainApp extends AbstractMainApp[SchedulingSystem with WithRamBenchmark] {
    override val initStrategy: (SchedulingSystem with WithRamBenchmark, Configuration) => Strategy = MemoryBWStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem with WithRamBenchmark =
        new HttpSchedulingSystem(config) with LoggingSchedulingSystem
}
