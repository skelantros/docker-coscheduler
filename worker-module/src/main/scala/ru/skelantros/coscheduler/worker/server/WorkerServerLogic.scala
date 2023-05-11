package ru.skelantros.coscheduler.worker.server

import cats.effect.{IO, Ref}
import cats.implicits._
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig}
import doobie.util.transactor.Transactor
import ru.skelantros.coscheduler.ledger.{Ledger, LedgerEvent, LedgerNote, LedgerTransactor}
import ru.skelantros.coscheduler.model.{SessionContext, Task}
import ru.skelantros.coscheduler.worker.WorkerConfiguration
import ru.skelantros.coscheduler.worker.docker.DockerClientResource
import ru.skelantros.coscheduler.worker.endpoints.{AppEndpoint, ServerResponse, WorkerEndpoints}
import ru.skelantros.coscheduler.worker.measurer.TaskSpeedMeasurer
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

import java.io.File
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class WorkerServerLogic(configuration: WorkerConfiguration, sessionCtxRef: Ref[IO, Option[SessionContext]]) extends ServerLogic {
    private implicit val transact: Transactor[IO] = LedgerTransactor.driverManager(configuration.db)

    private def withSessionCtx(f: SessionContext => IO[Unit]): IO[Unit] = for {
        ctxOpt <- sessionCtxRef.get
        action <- ctxOpt.fold(
            IO.println(s"Session context is undefined, so ledger won't work.")
        )(f)
    } yield action

    private def addLedgerNote(task: Task, event: LedgerEvent): IO[Unit] = withSessionCtx(addNoteWithCtx(task, event)(_))

    private def addNoteWithCtx(task: Task, event: LedgerEvent)(implicit ctx: SessionContext): IO[Unit] = for {
        ledgerNote <- LedgerNote.generate(configuration.node)(task, event)
        _ <- Ledger.addNote[IO](ledgerNote)
    } yield ()

    private val ledgerCompletionDelay = configuration.ledgerCompletionDelay.getOrElse(1.seconds)

    private def addLedgerNoteOnCompletionWithCtx(task: Task.Created)(implicit ctx: SessionContext): IO[Unit] = for {
        containerState <- containerState(task)
        isRunning = containerState.running
        action <-
            if (!isRunning) IO.println(s"Task $task is completed") >> LedgerNote.generate(configuration.node)(task, LedgerEvent.Completed).flatMap(Ledger.addNote[IO])
            else addLedgerNoteOnCompletionWithCtx(task).delayBy(ledgerCompletionDelay)
    } yield action

    // THIS IS NOT A BACKGROUND IO. USE .start TO RUN AND FORGET
    private def addLedgerNoteOnCompletion(task: Task.Created): IO[Unit] =
        withSessionCtx(addLedgerNoteOnCompletionWithCtx(task)(_))

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

    private def customTaskLogic[I, O](endpoint: AppEndpoint[I, O])(task: I => Task)(logic: I => IO[ServerResponse[O]]) =
        serverLogic(endpoint) { input =>
            if (task(input).node.id != configuration.node.id)
                IO.pure(ServerResponse.badRequest(s"Task ${task(input).id} is located on node ${task(input).node}."))
            else
                logic(input)
        }

    private def taskLogic[I <: Task, O](endpoint: AppEndpoint[I, O])(logic: I => IO[ServerResponse[O]]) =
        customTaskLogic(endpoint)(identity)(logic)

    private def containerState(task: Task.Created) =
        DockerClientResource(_.inspectContainer(task.containerId)).map(_.state)

    final val initSession = serverLogic(WorkerEndpoints.initSession) { ctx =>
        for {
            _ <- sessionCtxRef.set(ctx.some)
            _ <- IO.println(s"Node has been initialized with $ctx.")
        } yield ServerResponse.unit
    }

    final val build = serverLogic(WorkerEndpoints.build) { case (imageArchive, taskTitle) =>
        for {
            taskId <- uuid
            imageDir = new File(configuration.imagesFolder, taskId)
            _ <- unpackTar(imageArchive.file, imageDir)
            imageId <- DockerClientResource(_.build(imageDir.toPath))
            task = Task.Built(Task.TaskId(taskId), configuration.node, imageId, taskTitle)
        } yield ServerResponse(task)
    }

    final val create = customTaskLogic(WorkerEndpoints.create)(_._1) { case (task, cpusOpt) =>
        val containerConfig = ContainerConfig.builder().image(task.imageId).build()
        for {
            createResult <- DockerClientResource(_.createContainer(containerConfig))
            containerId = createResult.id
            _ <- cpusOpt match {
                case Some(cpus) =>
                    val hostConfig = HostConfig.builder().cpusetCpus(cpus.asString).build()
                    DockerClientResource(_.updateContainer(containerId, hostConfig)) >> IO.unit
                case _ => IO.unit
            }
            _ <- addLedgerNote(task, LedgerEvent.Created)
        } yield ServerResponse(task.created(createResult.id, cpusOpt))
    }

    final val start = taskLogic(WorkerEndpoints.start) { task =>
        for {
            state <- containerState(task)
            result <-
                if(state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is already running."))
                else
                    DockerClientResource(_.startContainer(task.containerId)) >>
                    addLedgerNote(task, LedgerEvent.Started) >>
                    addLedgerNoteOnCompletion(task).start >>
                    IO.pure(ServerResponse(task))
        } yield result
    }

    final val pause = taskLogic(WorkerEndpoints.pause) { task =>
        for {
            state <- containerState(task)
            result <-
                if(state.paused) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is already paused."))
                else if(!state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not running."))
                else
                    DockerClientResource(_.pauseContainer(task.containerId)) >>
                    addLedgerNote(task, LedgerEvent.Paused) >>
                    IO.pure(ServerResponse(task))
        } yield result
    }

    final val resume = taskLogic(WorkerEndpoints.resume) { task =>
        for {
            state <- containerState(task)
            result <-
                if(!state.paused) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not paused."))
                else if(!state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not running."))
                else
                    DockerClientResource(_.unpauseContainer(task.containerId)) >>
                    addLedgerNote(task, LedgerEvent.Resumed) >>
                    IO.pure(ServerResponse(task))
        } yield result
    }

    final val stop = taskLogic(WorkerEndpoints.stop) { task =>
        for {
            state <- containerState(task)
            result <-
                if(!state.running) IO.pure(ServerResponse.badRequest(s"A container for ${task.id} is not running."))
                else
                    DockerClientResource(_.stopContainer(task.containerId, 1)) >>
                    addLedgerNote(task, LedgerEvent.Stopped) >>
                    IO.pure(ServerResponse(task))
        } yield result
    }

    final val isRunning = taskLogic(WorkerEndpoints.isRunning) { task =>
        for {
            containerState <- DockerClientResource(_.inspectContainer(task.containerId).state)
        } yield ServerResponse(containerState.running)
    }

    final val nodeInfo = serverLogic(WorkerEndpoints.nodeInfo) { _ => ServerResponse(configuration.node).pure[IO] }

    private val sumOpts = (x: Option[Double], y: Option[Double]) => (x, y).mapN(_ + _)

    private def measureTaskSpeed(task: Task.Created, attempts: Int, duration: FiniteDuration): IO[ServerResponse[Double]] = for {
        resultOpts <- (0 until attempts).map(_ => TaskSpeedMeasurer(duration)(task)).toVector.sequence
        resultOpt = resultOpts.foldLeft(Option(0d))(sumOpts).map(_ / attempts)
        result = resultOpt.fold(
            ServerResponse.badRequest[Double](s"Incorrect task speed measurement result for task ${task.id}.")
        )(ServerResponse(_))
    } yield result

    final val taskSpeed = customTaskLogic(WorkerEndpoints.taskSpeed)(_._1) { case (task, attempts, duration) =>
        for {
            state <- containerState(task)
            result <-
                if(state.paused) ServerResponse.badRequest(s"A container for ${task.id} is paused.").pure[IO]
                else if(!state.running) ServerResponse.badRequest(s"A container for ${task.id} is not running.").pure[IO]
                else measureTaskSpeed(task, attempts, duration)
        } yield result
    }

    final override val routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = List(
        build,
        create,
        start,
        pause,
        resume,
        stop,
        isRunning,
        nodeInfo,
        taskSpeed,
        initSession
    )
}
