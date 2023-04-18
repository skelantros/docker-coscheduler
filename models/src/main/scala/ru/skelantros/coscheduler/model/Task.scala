package ru.skelantros.coscheduler.model

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import ru.skelantros.coscheduler.model.Task.TaskId

sealed trait Task {
    def id: TaskId
    def node: Node
    def imageId: String
    def title: String
}

object Task {
    final case class TaskId(id: String) extends AnyVal
    object TaskId {
        implicit val encoder: Encoder[TaskId] =
            Encoder.encodeString.contramap(_.id)

        implicit val decoder: Decoder[TaskId] =
            Decoder.decodeString.map(TaskId(_))
    }

    case class Built(id: TaskId, node: Node, imageId: String, title: String) extends Task {
        def created(containerId: String, cpus: Option[CpuSet]): Created =
            Created(id, node, imageId, title, containerId, cpus)
    }
    object Built {
        implicit val codec: Codec[Built] = deriveCodec
    }

    case class Created private(id: TaskId, node: Node, imageId: String, title: String, containerId: String, cpus: Option[CpuSet]) extends Task
    object Created {
        implicit val codec: Codec[Created] = deriveCodec
    }
}