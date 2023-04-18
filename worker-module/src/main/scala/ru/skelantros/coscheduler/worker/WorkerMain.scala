package ru.skelantros.coscheduler.worker

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.comcast.ip4s.{Ipv4Address, Port}
import org.http4s.ember.server.EmberServerBuilder
import ru.skelantros.coscheduler.model
import sttp.model.Uri
import sttp.tapir.server.http4s.Http4sServerInterpreter
import scala.sys.process._

object WorkerMain extends IOApp {
    private def parseArgs(args: List[String]): Option[(String, String, Ipv4Address, Port)] = args match {
        case nodeName :: nodeFolder :: host :: port :: _ =>
            (Some(nodeName), Some(nodeFolder), Ipv4Address.fromString(host), Port.fromString(port)).tupled
        case _ =>
            None
    }

    // TODO обернуть в IO
    private def cpusCount() =
        "grep -c ^processor /proc/cpuinfo".!!.dropRight(1).toIntOption

    private def workerConfiguration(nodeName: String, nodeFolder: String, host: Ipv4Address, port: Port) =
        for {
            cpus <- cpusCount()
            uri <- Uri.parse(s"http://$host:$port").toOption
        } yield WorkerConfiguration(nodeFolder, model.Node(nodeName, uri, cpus))

    private def makeServer(nodeName: String, nodeFolder: String, host: Ipv4Address, port: Port) =
        for {
            configuration <- workerConfiguration(nodeName, nodeFolder, host, port)
            serverLogic = new WorkerServerLogic(configuration)
            httpApp = Http4sServerInterpreter[IO].toRoutes(serverLogic.routes).orNotFound
            server = EmberServerBuilder
                .default[IO]
                .withHost(host)
                .withPort(port)
                .withHttpApp(httpApp)
                .build
                .use(_ => IO.never)
                .as(ExitCode.Success)
        } yield server

    override def run(args: List[String]): IO[ExitCode] =
        parseArgs(args).flatMap((makeServer _).tupled).getOrElse(IO.pure(ExitCode.Error))
}
