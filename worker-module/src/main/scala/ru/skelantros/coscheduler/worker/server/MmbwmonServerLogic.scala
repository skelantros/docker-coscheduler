package ru.skelantros.coscheduler.worker.server

import cats.effect.{IO, Resource}
import org.eclipse.paho.client.mqttv3.MqttClient
import ru.skelantros.coscheduler.worker.WorkerConfiguration
import ru.skelantros.coscheduler.worker.endpoints.{MmbwmonEndpoints, ServerResponse}
import ru.skelantros.coscheduler.worker.measurer.MmbwmonMeasurer
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.Uri
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class MmbwmonServerLogic(configuration: WorkerConfiguration)(implicit ec: ExecutionContext) extends ServerLogic {
    private def noMmbwmonResponse[A]: IO[ServerResponse[A]] = IO.pure(
        ServerResponse.badRequest("Node doesn't support mmbwmon. " +
            "Make sure variables `mmbwmonContainer` and `mqttUri` are defined in node's configuration.")
    )
    private def mqttClient(hostUri: Uri, clientId: String) =
        Resource.fromAutoCloseable(IO(new MqttClient(hostUri.toString, clientId)))

    final val measure = serverLogic(MmbwmonEndpoints.measure) { cpuSetOpt =>
        configuration.mqttUri match {
            case Some(hostUri) =>
                val action = for {
                    uuid <- this.uuid
                    clientId = s"${configuration.node.id}-$uuid"
                    // TODO создается большое количество клиентов, что может негативно повлиять на работу системы
                    resultOpt <- mqttClient(hostUri, clientId).use { client =>
                        IO.fromFuture(IO(MmbwmonMeasurer(clientId, client, cpuSetOpt)))
                    }
                    result <- resultOpt.fold(IO.raiseError[Double](new Exception(s"Incorrect mmbwmon result.")))(IO.pure(_))
                } yield ServerResponse(result)

                action.timeoutTo(60.seconds,
                    IO.pure(ServerResponse.internalError(s"Mmbwmon doesn't respond for 60 seconds. Make sure it's working on the node."))
                )
            case _ => noMmbwmonResponse
        }
    }
    override def routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = List(measure)
}
