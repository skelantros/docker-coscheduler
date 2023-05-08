package ru.skelantros.coscheduler.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import scala.concurrent.duration.FiniteDuration

import ru.skelantros.coscheduler.implicits._

case class SessionContext(sessionId: SessionId,
                          startTime: FiniteDuration)

object SessionContext {
    implicit val codec: Codec[SessionContext] = deriveCodec
}