package ru.skelantros.coscheduler.worker.measurer

import org.eclipse.paho.client.mqttv3._
import ru.skelantros.coscheduler.model.CpuSet

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.sys.process._

// TODO переписать на IO
object MmbwmonMeasurer {
    def hostname: String = "hostname".!!.dropRight(1)
    private val requestTopic = s"fast/agent/$hostname/mmbwmon/request"
    private val responseTopic = s"fast/agent/$hostname/mmbwmon/response"

    private def yaml(id: String, cpuSetOpt: Option[CpuSet]): String = {
        val cores = cpuSetOpt.getOrElse(CpuSet(0, 1)).cores
        s"""cores:
           |${cores.map(i => s"  - $i").mkString("\n")}
           |id: $id""".stripMargin
    }

    def apply(requestId: String, mqttClient: MqttClient, cpuSet: Option[CpuSet])(implicit ec: ExecutionContext): Future[Option[Double]] = {
        val msg = yaml(requestId, cpuSet)

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
