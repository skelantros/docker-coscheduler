package ru.skelantros.coscheduler.main.system

import ru.skelantros.coscheduler.worker.endpoints.EndpointError

case class EndpointException(error: EndpointError)
    extends Exception(s"Endpoint completed with status ${error.code}${error.msg.fold("")(msg => s", msg: $msg")}.")
