package ru.skelantros.coscheduler.worker.server

import cats.effect.IO
import ru.skelantros.coscheduler.worker.endpoints.{AppEndpoint, EndpointError, ServerResponse}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

import java.util.UUID

trait ServerLogic {
    self =>
    @inline
    final protected def serverLogic[I, O](endpoint: AppEndpoint[I, O])(logic: I => IO[ServerResponse[O]])=
        endpoint.serverLogic { input =>
            logic(input).handleErrorWith(t => IO(ServerResponse.internalError(t.toString)))
        }

    protected final val uuid: IO[String] = IO(UUID.randomUUID().toString.filter(_ != '-'))

    def routes: List[ServerEndpoint[Fs2Streams[IO], IO]]

    def ++(other: ServerLogic): ServerLogic = new ServerLogic {
        override def routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = self.routes ++ other.routes
    }
}
