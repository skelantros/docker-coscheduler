package ru.skelantros.coscheduler.model

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import ru.skelantros.coscheduler.model.Task.TaskId
import sttp.tapir.Schema

sealed trait Task {
    def id: TaskId
    def node: Node
    def imageId: String
}

object Task {
    final case class TaskId(id: String) extends AnyVal
    object TaskId {
        implicit val encoder: Encoder[TaskId] =
            Encoder.encodeString.contramap(_.id)

        implicit val decoder: Decoder[TaskId] =
            Decoder.decodeString.map(TaskId(_))
    }

    case class Built(id: TaskId, node: Node, imageId: String) extends Task {
        def created(containerId: String): Created =
            Created(id, node, imageId, containerId)
    }
    object Built {
        implicit val codec: Codec[Built] = deriveCodec
    }

    case class Created private(id: TaskId, node: Node, imageId: String, containerId: String) extends Task
    object Created {
        implicit val codec: Codec[Created] = deriveCodec
    }
}