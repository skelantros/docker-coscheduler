package ru.skelantros.coscheduler

import cats.implicits.catsSyntaxOptionId
import io.circe.{Decoder, Encoder}
import sttp.model.{StatusCode, Uri}
import sttp.tapir
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.Schema

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

object implicits {
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
}
