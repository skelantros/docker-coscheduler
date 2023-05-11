package ru.skelantros.coscheduler.main.app

import cats.effect.{ExitCode, IO, IOApp}
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.implicits.ParMapOps
import ru.skelantros.coscheduler.main.system.{EndpointException, HttpSchedulingSystem}
import ru.skelantros.coscheduler.model.Task

object SpeedTestApp extends IOApp with WithConfigLoad with DefaultLogger {
    override def loggerConfig: Logger.Config = Logger.defaultConfig

    private def measureLoop(params: Configuration.TaskSpeed, system: HttpSchedulingSystem)(task: Task.Created): IO[Unit] =
        system.speedOf(params.measurement.get)(task).flatMap(m =>
            log.info(task.title)(m) >> IO.sleep(params.waitBeforeMeasurement.get) >> measureLoop(params, system)(task)
        ).recoverWith {
            case EndpointException(t) => log.info(task.title)(s"Measurement completed with: $t") >> IO.unit
        }

    private def app(configuration: Configuration, speedTest: Configuration.SpeedTest): IO[Unit] = {
        val system = new HttpSchedulingSystem(configuration)

        for {
            node <- system.nodeInfo(speedTest.nodeUri)
            tasksStarted <- speedTest.tasks.parMap(
                system.buildTaskFromTuple(node)(_).flatMap(
                    system.createTask(_, None)
                ).flatMap(
                    system.startTask
                )
            )
            action <- tasksStarted.parMap(measureLoop(speedTest.params, system)) >> IO.unit
        } yield action
    }

    override def run(args: List[String]): IO[ExitCode] = {
        val cfg = loadConfiguration(args).get
        val speedTest = cfg.speedTest.get

        app(cfg, speedTest) >> IO.pure(ExitCode.Success)
    }
}
