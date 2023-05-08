package ru.skelantros.coscheduler.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class SessionId(id: String) extends AnyVal

object SessionId {
    implicit val codec: Codec[SessionId] = deriveCodec
}