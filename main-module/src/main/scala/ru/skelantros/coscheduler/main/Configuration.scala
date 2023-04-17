package ru.skelantros.coscheduler.main

import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.model.Node
import sttp.client3.UriContext

import java.io.File
import scala.concurrent.duration.{Duration, DurationInt}

case class Configuration(nodes: Set[Node], tasks: Vector[StrategyTask], waitForTaskDelay: Duration)

object Configuration {
    // TODO прикрутить подгрузку из конфига
    implicit val instance: Configuration =
        Configuration(
            Set(
                Node("node1", uri"http://0.0.0.0:9876"),
                Node("node2", uri"http://0.0.0.0:6789")
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