package ru.skelantros.coscheduler.worker.server

import org.eclipse.paho.client.mqttv3._

import scala.sys.process._
import scala.concurrent.{ExecutionContext, Future, Promise}

// TODO переписать на IO
object MmbwmonMeasurer {
    def hostname: String = "hostname".!!.dropRight(1)
    private val requestTopic = s"fast/agent/$hostname/mmbwmon/request"
    private val responseTopic = s"fast/agent/$hostname/mmbwmon/response"

    def apply(requestId: String, mqttClient: MqttClient)(implicit ec: ExecutionContext): Future[Option[Double]] = {
        val msg =
            s"""cores:
               |  - 0
               |id: $requestId""".stripMargin

        val mqttMsg = new MqttMessage(msg.getBytes)
        mqttMsg.setQos(2)

        val p = Promise[String]()
        mqttClient.connect(new MqttConnectOptions)
        mqttClient.subscribe(responseTopic, (_, msg) => {
            val payload = new String(msg.getPayload)
            if (payload.contains(requestId)) {
                p.success(payload)
            }
        })

        mqttClient.publish(requestTopic, mqttMsg)

        for {
            payload <- p.future
            result = payload.linesIterator.find(_.contains("result: ")).flatMap(_.drop(8).toDoubleOption)
            _ <- Future { mqttClient.disconnect() }
        } yield result
    }
}
