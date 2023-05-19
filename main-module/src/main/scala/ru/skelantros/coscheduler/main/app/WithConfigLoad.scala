package ru.skelantros.coscheduler.main.app

import pureconfig.ConfigSource
import ru.skelantros.coscheduler.main.Configuration
import pureconfig.generic.auto._

trait WithConfigLoad {
    protected def loadConfiguration(args: List[String]): Option[Configuration] = {
        val res = args.headOption.fold(ConfigSource.default)(ConfigSource.file).load[Configuration]
        res.left.foreach(println)
        res.toOption
    }
}
