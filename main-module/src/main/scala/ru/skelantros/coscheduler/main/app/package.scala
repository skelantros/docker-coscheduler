package ru.skelantros.coscheduler.main

import pureconfig.ConfigReader
import pureconfig.error.FailureReason
import sttp.model.Uri

package object app {
    private def failureReason(str: String): FailureReason = new FailureReason {
        override def description: String = str
    }

    implicit val uriReader: ConfigReader[Uri] =
        ConfigReader.stringConfigReader.emap(Uri.parse(_).left.map(failureReason))
}
