package ru.skelantros.coscheduler.worker

import pureconfig.ConfigReader
import pureconfig.error.FailureReason
import pureconfig.generic.semiauto.deriveReader
import ru.skelantros.coscheduler.ledger.LedgerTransactor
import ru.skelantros.coscheduler.model.Node
import sttp.model.Uri
import ru.skelantros.coscheduler.implicits._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.sys.process._

case class WorkerConfiguration(imagesFolder: String, node: Node, mqttUri: Option[Uri], ledgerCompletionDelay: Option[FiniteDuration], db: LedgerTransactor.Config)

object WorkerConfiguration {
    private case class PartialNode(id: String, uri: Uri, cores: Option[Int]) {
        def toNode(defaultCores: =>Int): Node = Node(id, uri, cores.getOrElse(defaultCores))
    }

    // TODO обернуть в IO
    private def cpusCount() =
        "grep -c ^processor /proc/cpuinfo".!!.dropRight(1).toInt

    private def failureReason(str: String): FailureReason = new FailureReason {
        override def description: String = str
    }

    private implicit val uriReader: ConfigReader[Uri] =
        ConfigReader.stringConfigReader.emap(Uri.parse(_).left.map(failureReason))

    private implicit val nodeReader: ConfigReader[Node] =
        deriveReader[PartialNode].map(_.toNode(cpusCount()))

    implicit val reader: ConfigReader[WorkerConfiguration] = deriveReader

    implicit val dbReader: ConfigReader[LedgerTransactor.Config] = deriveReader
}