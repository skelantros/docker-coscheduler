package ru.skelantros.coscheduler.main

import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.model.Node
import sttp.client3.UriContext
import sttp.model.Uri

import java.io.File
import scala.concurrent.duration.{Duration, DurationInt}

case class Configuration(nodesUri: Vector[Uri],
                         tasks: Vector[StrategyTask],
                         waitForTaskDelay: Duration,
                         bwThreshold: Option[Double])