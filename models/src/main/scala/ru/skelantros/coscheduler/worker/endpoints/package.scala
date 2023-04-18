package ru.skelantros.coscheduler.worker

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{StatusCode, Uri}
import sttp.tapir.{Endpoint, Schema}

package object endpoints {
    implicit val statusCodeEncoder: Encoder[StatusCode] =
        Encoder.encodeInt.contramap(_.code)

    implicit val statusCodeDecoder: Decoder[StatusCode] =
        Decoder.decodeInt.map(StatusCode(_))

    implicit val uriSchema: Schema[Uri] =
        Schema.schemaForString.map(Uri.parse(_).toOption)(_.toString)

    type ServerResponse[+A] = Either[EndpointError, A]

    object ServerResponse {
        def apply[A](x: A): ServerResponse[A] = Right(x)
        def unit: ServerResponse[Unit] = Right(())
        def error[A](code: StatusCode, msg: String = ""): ServerResponse[A] = Left(EndpointError(code, msg))
        def badRequest[A](msg: String = ""): ServerResponse[A] = error(StatusCode.BadRequest, msg)
        def notFound[A](msg: String = ""): ServerResponse[A] = error(StatusCode.NotFound, msg)
        def internalError[A](msg: String = ""): ServerResponse[A] = error(StatusCode.InternalServerError, msg)
    }

    type AppEndpoint[I, O] = Endpoint[Unit, I, EndpointError, O, Fs2Streams[IO]]
}
