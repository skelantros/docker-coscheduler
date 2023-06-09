package ru.skelantros.coscheduler.worker

import cats.effect.{ExitCode, IO, IOApp, Ref}
import com.comcast.ip4s.{Ipv4Address, Port}
import org.http4s.ember.server.EmberServerBuilder
import pureconfig.ConfigSource
import ru.skelantros.coscheduler.model.SessionContext
import ru.skelantros.coscheduler.worker.server.{MmbwmonServerLogic, WorkerServerLogic}
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object WorkerMain extends IOApp {
    private def loadConfiguration(args: List[String]): Option[WorkerConfiguration] =
        args.headOption.fold(ConfigSource.default)(ConfigSource.file).load[WorkerConfiguration].toOption

    private def makeServer(configuration: WorkerConfiguration, ctxRef: Ref[IO, Option[SessionContext]]) = {
        val serverLogic =
            (new WorkerServerLogic(configuration, ctxRef) ++ new MmbwmonServerLogic(configuration)(ExecutionContext.global)).routes

        val httpApp = Http4sServerInterpreter[IO].toRoutes(serverLogic).orNotFound

        for {
            host <- configuration.node.host.flatMap(Ipv4Address.fromString)
            port <- configuration.node.port.flatMap(Port.fromInt)
        } yield EmberServerBuilder
            .default[IO]
            .withHost(host)
            .withPort(port)
            .withHttpApp(httpApp)
            .build
            .use(_ => IO.never)
            .as(ExitCode.Success)
    }

    override def run(args: List[String]): IO[ExitCode] = for {
        ref <- Ref.of[IO, Option[SessionContext]](None)
        res <- loadConfiguration(args).flatMap(makeServer(_, ref)).getOrElse(IO.pure(ExitCode.Error))
    } yield res
}
