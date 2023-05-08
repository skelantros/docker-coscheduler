package ru.skelantros.coscheduler.worker.endpoints

import cats.effect.IO
import ru.skelantros.coscheduler.image.ImageArchive
import ru.skelantros.coscheduler.model.{CpuSet, Node, SessionContext, Task}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import ru.skelantros.coscheduler.implicits._

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.FiniteDuration

object WorkerEndpoints {
    private val baseEndpoint = endpoint.in("docker").errorOut(jsonBody[EndpointError])

    private val imageArchiveBody =
        fileBody.mapDecode(file => DecodeResult.fromOption(Some(ImageArchive(file))))(_.file)

    private val builtBody = jsonBody[Task.Built]
    private val createdBody = jsonBody[Task.Created]

    final val initSession = baseEndpoint.post
        .in("initSession")
        .in(jsonBody[SessionContext])

    final val build = baseEndpoint.post
        .in("build")
        .in(imageArchiveBody)
        .in(query[String]("title"))
        .out(builtBody)

    private def taskEndpoint = baseEndpoint.post.in(createdBody).out(createdBody)
    final val create = baseEndpoint.post
        .in("create")
        .in(builtBody).in(query[Option[CpuSet]]("cpus"))
        .out(createdBody)
    final val start = taskEndpoint.in("start")
    final val pause = taskEndpoint.in("pause")
    final val resume = taskEndpoint.in("resume")
    final val stop = taskEndpoint.in("stop")

    final val taskLogs = baseEndpoint.post
        .in("logs")
        .in(createdBody)
        .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

    final val isRunning = baseEndpoint.post
        .in("running")
        .in(createdBody)
        .out(jsonBody[Boolean])

    final val nodeInfo = baseEndpoint.get
        .in("info")
        .out(jsonBody[Node])

    final val taskSpeed = baseEndpoint.post
        .in("taskSpeed")
        .in(createdBody)
        .in(query[FiniteDuration]("durationMs"))
        .out(jsonBody[Double])
}
