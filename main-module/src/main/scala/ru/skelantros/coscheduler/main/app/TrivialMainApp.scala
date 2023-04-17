package ru.skelantros.coscheduler.main.app

import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.{Strategy, TrivialStrategy}
import ru.skelantros.coscheduler.main.system.{HttpSchedulingSystem, LoggingSchedulingSystem, SchedulingSystem}

object TrivialMainApp extends AbstractMainApp[SchedulingSystem] {
    override val initStrategy: (SchedulingSystem, Configuration) => Strategy = TrivialStrategy(_, _)

    override def schedulingSystem(config: Configuration): SchedulingSystem =
        new HttpSchedulingSystem(config) with LoggingSchedulingSystem

    override def loadConfiguration(args: List[String]): Option[Configuration] = Some(implicitly[Configuration])
}
