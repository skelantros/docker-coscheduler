package ru.skelantros.coscheduler.main.system
import cats.effect.IO
import ru.skelantros.coscheduler.model.{Node, Task}
import ru.skelantros.coscheduler.main.system.SchedulingSystem.TaskLogs
import ru.skelantros.coscheduler.image.ImageArchive
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.worker.endpoints.{AppEndpoint, WorkerEndpoints}
import sttp.client3.http4s.Http4sBackend
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp.SttpClientInterpreter

class HttpSchedulingSystem(val config: Configuration) extends SchedulingSystem {
    private val client = Http4sBackend.usingDefaultEmberClientBuilder[IO]()
    private val inter = SttpClientInterpreter()
    private def makeRequest[I, O](node: Node, endpoint: AppEndpoint[I, O])(input: I): IO[O] = for {
        route <- IO(inter.toRequest(endpoint, Some(node.uri)))
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

    override def buildTask(node: Node)(image: ImageArchive, imageName: Option[String] = None): IO[Task.Built] =
        makeRequest(node, WorkerEndpoints.build)(image, imageName)

    override def createTask(task: Task.Built): IO[Task.Created] =
        makeRequest(task.node, WorkerEndpoints.create)(task)

    override def startTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node, WorkerEndpoints.start)(task)

    override def pauseTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node, WorkerEndpoints.pause)(task)

    override def resumeTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node, WorkerEndpoints.resume)(task)

    override def stopTask(task: Task.Created): IO[Task.Created] =
        makeRequest(task.node, WorkerEndpoints.stop)(task)

    override def waitForTask(task: Task.Created): IO[Option[TaskLogs]] =
        for {
            isRunning <- makeRequest(task.node, WorkerEndpoints.isRunning)(task)
            res <-
                if(isRunning) waitForTask(task).delayBy(config.waitForTaskDelay)
                else taskLogs(task)
        } yield res

    override def taskLogs(task: Task.Created): IO[Option[TaskLogs]] =
        makeRequest(task.node, WorkerEndpoints.taskLogs)(task) >> IO.pure(Some("Result"))
}
