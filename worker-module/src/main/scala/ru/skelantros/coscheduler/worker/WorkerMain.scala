package ru.skelantros.coscheduler.worker

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import org.http4s.ember.server.EmberServerBuilder
import ru.skelantros.coscheduler.model
import sttp.client3.UriContext
import sttp.tapir.server.http4s.Http4sServerInterpreter

object WorkerMain extends IOApp {
    val port = 9876
    val workerConfiguration = WorkerConfiguration("/home/skelantros/docker_experiments/node1", model.Node("node1", uri"localhost:$port"))
    println(workerConfiguration)
    val serverLogic = new WorkerServerLogic(workerConfiguration)
    val routes = Http4sServerInterpreter[IO].toRoutes(serverLogic.routes).orNotFound

    override def run(args: List[String]): IO[ExitCode] =
        EmberServerBuilder
            .default[IO]
            .withHost(ipv4"0.0.0.0")
            .withPort(port"9876")
            .withHttpApp(routes)
            .build
            .use(_ => IO.never)
            .as(ExitCode.Success)
}
