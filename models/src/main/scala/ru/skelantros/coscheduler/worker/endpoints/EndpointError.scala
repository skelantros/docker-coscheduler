package ru.skelantros.coscheduler.worker.endpoints

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.model.StatusCode
import sttp.tapir.codec.enumeratum._

case class EndpointError(code: StatusCode, msg: Option[String])
object EndpointError {

    implicit val codec: Codec[EndpointError] = deriveCodec

    def apply(code: StatusCode, msg: String = ""): EndpointError =
        EndpointError(code, Option(msg).filter(_.nonEmpty))
}