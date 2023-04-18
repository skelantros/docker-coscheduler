package ru.skelantros.coscheduler.main

import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.model.Node
import sttp.client3.UriContext
import sttp.model.Uri

import java.io.File
import scala.concurrent.duration.{Duration, DurationInt}

case class Configuration(nodesUri: Vector[Uri], tasks: Vector[StrategyTask], waitForTaskDelay: Duration)

object Configuration {
    // TODO прикрутить подгрузку из конфига
    implicit val instance: Configuration =
        Configuration(
            Vector(
                uri"http://0.0.0.0:9876",
                uri"http://0.0.0.0:6789"
            ),
            Vector(
                "DockerTest" -> new File("/home/skelantros/projects/DockerTest"),
                "DockerTest2" -> new File("/home/skelantros/projects/DockerTest"),
                "DockerTest3" -> new File("/home/skelantros/projects/DockerTest"),
                "DockerTest4" -> new File("/home/skelantros/projects/DockerTest")
            ),
            1.seconds
        )
}