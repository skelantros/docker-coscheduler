package ru.skelantros.coscheduler.worker.endpoints

import ru.skelantros.coscheduler.model.CpuSet
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object MmbwmonEndpoints {
    private def baseEndpoint = endpoint.in("mmbwmon").post.errorOut(jsonBody[EndpointError])
    final val measure = baseEndpoint.in("measure").in(query[Option[CpuSet]]("cpus")).out(jsonBody[Double])
}
