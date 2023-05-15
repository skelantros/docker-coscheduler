package ru.skelantros.coscheduler.main.strategy

import cats.Show
import cats.effect.IO
import ru.skelantros.coscheduler.main.Configuration
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.main.system.SchedulingSystem
import ru.skelantros.coscheduler.model.{Node, SessionContext, SessionId}
import cats.implicits._
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.implicits._

import java.io.File
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

trait Strategy extends DefaultLogger {
    def schedulingSystem: SchedulingSystem
    def config: Configuration

    override lazy val loggerConfig: Logger.Config = config.strategyLogging

    def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Strategy.PartialInfo]

    private val sessionContext: IO[SessionContext] = for {
        uuid <- IO(UUID.randomUUID().toString.filter(_ != '-'))
        startTime <- IO.realTime
    } yield SessionContext(SessionId(uuid), startTime)

    final def executeWithInit(tasks: Vector[StrategyTask]): IO[Strategy.Info] = for {
        _ <- log.info("")(tasks.toString)
        sc <- sessionContext
        nodes <- config.nodesUri.parMap(schedulingSystem.nodeInfo)
        _ <- nodes.parMap(schedulingSystem.initSession(sc))
        _ <- log.info("")(s"SessionID = ${sc.sessionId}. Starting strategy execution...")
        actionWithTime <- execute(nodes, tasks).withTime
        _ <- log.info("")(s"Strategy execution has been completed. Total time is ${actionWithTime._2}")
    } yield actionWithTime._1.toInfo(actionWithTime._2)
}

object Strategy {
    type TaskName = String
    type StrategyTask = (TaskName, File)

    case class PartialInfo(preStageTime: Option[FiniteDuration]) {
        def toInfo(totalTime: FiniteDuration): Info = Info(totalTime, this.preStageTime)
    }
    case class Info(totalTime: FiniteDuration, preStageTime: Option[FiniteDuration])

    object Info {
        implicit val show: Show[Info] = _.toString
    }
}