package ru.skelantros.coscheduler.main.strategy

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

trait Strategy extends DefaultLogger {
    def schedulingSystem: SchedulingSystem
    def config: Configuration

    override lazy val loggerConfig: Logger.Config = config.strategyLogging

    def execute(nodes: Vector[Node], tasks: Vector[StrategyTask]): IO[Unit]

    private val sessionContext: IO[SessionContext] = for {
        uuid <- IO(UUID.randomUUID().toString.filter(_ != '-'))
        startTime <- IO.realTime
    } yield SessionContext(SessionId(uuid), startTime)

    final def executeWithInit(tasks: Vector[StrategyTask]): IO[Unit] = for {
        sc <- sessionContext
        nodes <- config.nodesUri.parMap(schedulingSystem.nodeInfo)
        _ <- nodes.parMap(schedulingSystem.initSession(sc))
        _ <- log.info("")(s"SessionID = ${sc.sessionId}. Starting strategy execution...")
        action <- execute(nodes, tasks)
    } yield action
}

object Strategy {
    type TaskName = String
    type StrategyTask = (TaskName, File)
}