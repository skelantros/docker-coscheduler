package ru.skelantros.coscheduler.worker

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import io.circe.{Decoder, Encoder}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{StatusCode, Uri}
import sttp.tapir
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.{Endpoint, Schema}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

package object endpoints {
    implicit val statusCodeEncoder: Encoder[StatusCode] =
        Encoder.encodeInt.contramap(_.code)

    implicit val statusCodeDecoder: Decoder[StatusCode] =
        Decoder.decodeInt.map(StatusCode(_))

    implicit val uriSchema: Schema[Uri] =
        Schema.schemaForString.map(Uri.parse(_).toOption)(_.toString)

    implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
        Encoder.encodeLong.contramap(_.toNanos)

    implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
        Decoder.decodeLong.map(FiniteDuration(_, TimeUnit.NANOSECONDS))

    implicit val finiteDurationSchema: Schema[FiniteDuration] =
        Schema.schemaForLong.map(FiniteDuration(_, TimeUnit.NANOSECONDS).some)(_.toNanos)

    implicit val nsDurationQueryCodec: tapir.Codec[List[String], FiniteDuration, TextPlain] =
        tapir.Codec.listHead[String, Long, TextPlain].map(Duration.fromNanos _)(_.toNanos)

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
