package ru.skelantros.coscheduler.worker

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.LogStream
import com.spotify.docker.client.messages.ContainerConfig
import fs2._
import ru.skelantros.coscheduler.model.Task
import ru.skelantros.coscheduler.worker.docker.DockerClientResource
import ru.skelantros.coscheduler.worker.endpoints.{AppEndpoint, ServerResponse, WorkerEndpoints}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

import java.io.File
import java.util.UUID

class WorkerServerLogic(configuration: WorkerConfiguration) {
    private def createDirectory(dir: File): IO[Unit] = {
        import scala.sys.process._
        IO(s"mkdir $dir".!) >> IO.unit
    }

    private def unpackTar(src: File, targetDir: File): IO[Unit] = {
        import scala.sys.process._
        createDirectory(targetDir) >> IO(s"tar -xzf $src -C $targetDir".!).flatMap {
            case 0 => IO.unit
            case code => IO.raiseError(new Exception(s"error code when unpacking $src: $code"))
        }
    }

    private val uuid: IO[String] = IO(UUID.randomUUID().toString.filter(_ != '-'))

    @inline
    private def serverLogic[I, O](endpoint: AppEndpoint[I, O])(logic: I => IO[ServerResponse[O]]) =
        endpoint.serverLogic { input =>
            logic(input).handleErrorWith(t => IO(ServerResponse.internalError(t.toString)))
        }

    private def taskLogic[I <: Task, O](endpoint: AppEndpoint[I, O])(logic: I => IO[ServerResponse[O]]) =
        serverLogic(endpoint) { task =>
            if (task.node != configuration.node)
                IO.pure(ServerResponse.badRequest(s"Task ${task.id} is located on node ${task.node}."))
            else
                logic(task)
        }

    private def containerState(task: Task.Created) =
        DockerClientResource(_.inspectContainer(task.containerId)).map(_.state)

    final val build = serverLogic(WorkerEndpoints.build) { case (imageArchive, taskTitle) =>
        for {
            taskId <- uuid
            imageDir = new File(configuration.imagesFolder, taskId)
            _ <- unpackTar(imageArchive.file, imageDir)
            imageId <- DockerClientResource(_.build(imageDir.toPath))
            task = Task.Built(Task.TaskId(taskId), configuration.node, imageId, taskTitle)
        } yield ServerResponse(task)
    }

    final val create = taskLogic(WorkerEndpoints.create) { task =>
        val containerConfig = ContainerConfig.builder().image(task.imageId).build()
        for {
            createResult <- DockerClientResource(_.createContainer(containerConfig))
        } yield ServerResponse(task.created(createResult.id))
    }

    final val start = taskLogic(WorkerEndpoints.start) { task =>
        for {
            state <- containerState(task)
            result <-
                if(state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is already running."))
                else DockerClientResource(_.startContainer(task.containerId)) >> IO.pure(ServerResponse(task))
        } yield result
    }

    final val pause = taskLogic(WorkerEndpoints.pause) { task =>
        for {
            state <- containerState(task)
            result <-
                if(state.paused) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is already paused."))
                else if(!state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not running."))
                else DockerClientResource(_.pauseContainer(task.containerId)) >> IO.pure(ServerResponse(task))
        } yield result
    }

    final val resume = taskLogic(WorkerEndpoints.resume) { task =>
        for {
            state <- containerState(task)
            result <-
                if(!state.paused) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not paused."))
                else if(!state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not running."))
                else DockerClientResource(_.unpauseContainer(task.containerId)) >> IO.pure(ServerResponse(task))
        } yield result
    }

    final val stop = taskLogic(WorkerEndpoints.stop) { task =>
        for {
            state <- containerState(task)
            result <-
                if(!state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not running."))
                else DockerClientResource(_.stopContainer(task.containerId, 1)) >> IO.pure(ServerResponse(task))
        } yield result
    }

    final val isRunning = taskLogic(WorkerEndpoints.isRunning) { task =>
        for {
            containerState <- DockerClientResource(_.inspectContainer(task.containerId).state)
        } yield ServerResponse(containerState.running)
    }

    final val nodeInfo = serverLogic(WorkerEndpoints.nodeInfo) { _ => ServerResponse(configuration.node).pure[IO] }

    // FIXME
    private def fs2LogStream(logStream: LogStream) = {
        fs2.Stream.unfold[IO, LogStream, String](logStream)(remLogs => if(remLogs.hasNext) Some((new String(remLogs.next.content().array()), remLogs)) else {remLogs.close(); None})
            .flatMap(str => Stream.chunk(Chunk.array(str.toCharArray)))
            .map(_.toByte)
    }

    final val taskLogs = taskLogic(WorkerEndpoints.taskLogs) { task =>
        for {
            logs <- DockerClientResource(_.logs(task.containerId, LogsParam.stdout, LogsParam.stderr))
        } yield ServerResponse(fs2LogStream(logs))
    }

    final val routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = List(
        build,
        create,
        start,
        pause,
        resume,
        stop,
        isRunning,
        taskLogs,
        nodeInfo
    )
}
