package ru.skelantros.coscheduler.worker

import ru.skelantros.coscheduler.model.Node
import sttp.model.Uri

case class WorkerConfiguration(imagesFolder: String, node: Node, mqttUri: Option[Uri])
