package ru.skelantros.coscheduler.worker

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.comcast.ip4s.{Ipv4Address, Port}
import org.http4s.ember.server.EmberServerBuilder
import ru.skelantros.coscheduler.model
import ru.skelantros.coscheduler.worker.server.{MmbwmonServerLogic, WorkerServerLogic}
import sttp.model.Uri
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext
import scala.sys.process._

object WorkerMain extends IOApp {
    private def parseArgs(args: List[String]): Option[(String, String, Ipv4Address, Port, Option[Uri])] = args match {
        case nodeName :: nodeFolder :: host :: port :: argss =>
            (Some(nodeName), Some(nodeFolder), Ipv4Address.fromString(host), Port.fromString(port), argss.headOption.map(Uri.parse(_).toOption)).tupled
        case _ =>
            None
    }

    // TODO обернуть в IO
    private def cpusCount() =
        "grep -c ^processor /proc/cpuinfo".!!.dropRight(1).toIntOption

    private def workerConfiguration(nodeName: String, nodeFolder: String, host: Ipv4Address, port: Port, mqttUri: Option[Uri]) =
        for {
            cpus <- cpusCount()
            uri <- Uri.parse(s"http://$host:$port").toOption
        } yield WorkerConfiguration(nodeFolder, model.Node(nodeName, uri, cpus), mqttUri)

    private def makeServer(nodeName: String, nodeFolder: String, host: Ipv4Address, port: Port, mqttUri: Option[Uri]) =
        for {
            configuration <- workerConfiguration(nodeName, nodeFolder, host, port, mqttUri: Option[Uri])
            serverLogic = new WorkerServerLogic(configuration)
            mmbwmonLogic = new MmbwmonServerLogic(configuration)(ExecutionContext.global)
            httpApp = Http4sServerInterpreter[IO].toRoutes(serverLogic.routes ++ mmbwmonLogic.routes).orNotFound
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
