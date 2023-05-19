package ru.skelantros.coscheduler.model

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import ru.skelantros.coscheduler.model.Task.TaskId

import cats.implicits._

sealed trait Task { self =>
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
            Created(id, node, imageId, title, containerId)(cpus)

        def updatedNode(node: Node): Built = Built(id, node, imageId, title)
    }
    object Built {
        implicit val codec: Codec[Built] = deriveCodec
    }

    case class Created private(id: TaskId, node: Node, imageId: String, title: String, containerId: String)(val cpus: Option[CpuSet]) extends Task {
        def updatedCpus(cpus: Option[CpuSet]): Task.Created = Created(id, node, imageId, title, containerId)(cpus)

        def updatedNode(node: Node): Created = Created(id, node, imageId, title, containerId)(cpus)
    }
    object Created {
        private case class CreatedUncurried(id: TaskId, node: Node, imageId: String, title: String, containerId: String, cpus: Option[CpuSet]) {
            def toTask: Task.Created = Task.Created(id, node, imageId, title, containerId)(cpus)
        }
        private def toUncurried(task: Task.Created): CreatedUncurried =
            CreatedUncurried(task.id, task.node, task.imageId, task.title, task.containerId, task.cpus)

        implicit val codec: Codec[Created] = deriveCodec[CreatedUncurried].iemap(_.toTask.asRight)(toUncurried)
    }
}