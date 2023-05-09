package ru.skelantros.coscheduler.worker

import sttp.model.StatusCode
import sttp.tapir.Endpoint

package object endpoints {
    type ServerResponse[+A] = Either[EndpointError, A]

    object ServerResponse {
        def apply[A](x: A): ServerResponse[A] = Right(x)
        def unit: ServerResponse[Unit] = Right(())
        def error[A](code: StatusCode, msg: String = ""): ServerResponse[A] = Left(EndpointError(code, msg))
        def badRequest[A](msg: String = ""): ServerResponse[A] = error(StatusCode.BadRequest, msg)
        def notFound[A](msg: String = ""): ServerResponse[A] = error(StatusCode.NotFound, msg)
        def internalError[A](msg: String = ""): ServerResponse[A] = error(StatusCode.InternalServerError, msg)
    }

    type AppEndpoint[I, O] = Endpoint[Unit, I, EndpointError, O, Any]
}
