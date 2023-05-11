package ru.skelantros.coscheduler.main.system
import cats.effect.IO
import ru.skelantros.coscheduler.model.{CpuSet, Node, SessionContext, Task}
import ru.skelantros.coscheduler.image.ImageArchive
import ru.skelantros.coscheduler.logging.Logger
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.system.WithTaskSpeedEstimate.TaskSpeed
import ru.skelantros.coscheduler.worker.endpoints.{AppEndpoint, MmbwmonEndpoints, WorkerEndpoints}
import sttp.client3.http4s.Http4sBackend
import sttp.model.Uri
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp.SttpClientInterpreter

import scala.concurrent.duration.FiniteDuration

class HttpSchedulingSystem(val config: Configuration)
    extends SchedulingSystem
    with WithMmbwmon
    with WithTaskSpeedEstimate {

    private val client = Http4sBackend.usingDefaultEmberClientBuilder[IO]()
    private val inter = SttpClientInterpreter()

    private def makeRequest[I, O](uri: Uri, endpoint: AppEndpoint[I, O])(input: I): IO[O] = for {
        route <- IO(inter.toRequest(endpoint, Some(uri)))
        request = route(input)
        response <- client.use(b => request.send(b))
        result <- response.body match {
            case DecodeResult.Value(Right(value)) =>
                IO.pure(value)
            case DecodeResult.Value(Left(error)) =>
                IO.raiseError(EndpointException(error))
            case decodeResult =>
                IO.raiseError(new Exception(s"Failed to decode result of the request. Result is $decodeResult."))
        }
    } yield result

    override def nodeInfo(uri: Uri): IO[Node] =
        makeRequest(uri, WorkerEndpoints.nodeInfo)(()).map(_.copy(uri = uri))

    override def buildTask(node: Node)(image: ImageArchive, taskName: String): IO[Task.Built] =
        makeRequest(node.uri, WorkerEndpoints.build)(image, taskName).map(_.copy(node = node))

    override def createTask(task: Task.Built, cpuset: Option[CpuSet] = None): IO[Task.Created] =
        makeRequest(task.node.uri, WorkerEndpoints.create)(task, cpuset).map(_.copy(node = task.node))

    override def startTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node.uri, WorkerEndpoints.start)(task).map(_.copy(node = task.node))

    override def pauseTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node.uri, WorkerEndpoints.pause)(task).map(_.copy(node = task.node))

    override def resumeTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node.uri, WorkerEndpoints.resume)(task).map(_.copy(node = task.node))

    override def stopTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node.uri, WorkerEndpoints.stop)(task).map(_.copy(node = task.node))

    override def waitForTask(task: Task.Created): IO[Boolean] = {
        def go(task: Task.Created): IO[Boolean] =
            for {
                isRunning <- makeRequest(task.node.uri, WorkerEndpoints.isRunning)(task)
                res <-
                    if(isRunning) go(task).delayBy(config.waitForTaskDelay)
                    else IO.pure(true)
            } yield res

        go(task)
    }

    override def isRunning(task: Task.Created): IO[Boolean] =
        makeRequest(task.node.uri, WorkerEndpoints.isRunning)(task)

    // 1 - (measured - 0.33) / (1 - 0.33) = 1 + 0.33/0.67 - measured/0.67 = 1 / 0.67 - measured / 0.67 ~=~ 3/2 - 3/2 * measured = 3/2 (1 - measured)
    override def mmbwmon(node: Node): IO[Double] = for {
        measured <- makeRequest(node.uri, MmbwmonEndpoints.measure)(())
    } yield 3 * (1 - measured) / 2

    override def speedOf(duration: FiniteDuration, attempts: Int)(task: Task.Created): IO[TaskSpeed] =
        makeRequest(task.node.uri, WorkerEndpoints.taskSpeed)(task, attempts, duration)

    override def initSession(sessionCtx: SessionContext)(node: Node): IO[Unit] =
        makeRequest(node.uri, WorkerEndpoints.initSession)(sessionCtx)
}

object HttpSchedulingSystem {
    def withLogging(config: Configuration): HttpSchedulingSystem with LoggingSchedulingSystem =
        new HttpSchedulingSystem(config) with LoggingSchedulingSystem {
            override def loggerConfig: Logger.Config = config.schedulingSystemLogging
        }
}