package ru.skelantros.coscheduler.main.app.snippets

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.app.WithConfigLoad
import ru.skelantros.coscheduler.main.implicits._
import ru.skelantros.coscheduler.main.system.HttpSchedulingSystem
import ru.skelantros.coscheduler.model.CpuSet
import sttp.client3.UriContext

import java.io.File
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object TwoTasksLaunch extends IOApp with DefaultLogger with WithConfigLoad {
    override def loggerConfig: Logger.Config = Logger.defaultConfig

    override def run(args: List[String]): IO[ExitCode] = {
        val system = HttpSchedulingSystem.withLogging(loadConfiguration(args).get)

        val task1 = ("BT-C", new File("/home/skelantros/coscheduler/tasks/BT-C"))
        val task2 = ("LU-C", new File("/home/skelantros/coscheduler/tasks/LU-C"))
        val nodeUri = uri"http://192.168.0.103:6789"

        def runTasks(cpuSet1: Option[CpuSet], cpuSet2: Option[CpuSet], measureDuration: FiniteDuration, delay: FiniteDuration): IO[Unit] = for {
            node <- system.nodeInfo(nodeUri)
            runTask1 <- system.runTaskFromTuple(node)(task1, cpuSet1)
            runTask2 <- system.runTaskFromTuple(node)(task2, cpuSet2)
            speedsMeasures <- (system.speedOf(measureDuration)(runTask1), system.speedOf(measureDuration)(runTask2)).parTupled.delayBy(delay)
            wait <- (system.waitForTask(runTask1), system.waitForTask(runTask2)).parTupled.withTime
            _ <- log.info("")(s"time: ${wait._2}\ntask1.speed = ${speedsMeasures._1}\ntask2.speed = ${speedsMeasures._2}")
        } yield ()

        val partitionRun = runTasks(Some(CpuSet(0, 6)), Some(CpuSet(6, 6)), 10.seconds, 2.seconds)
        val sameRun = runTasks(None, None, 10.seconds, 2.seconds)

        def stupidRun(cpuSet1: CpuSet, cpuSet2: CpuSet, measureDuration: FiniteDuration, delay: FiniteDuration): IO[Unit] = for {
            node <- system.nodeInfo(nodeUri)
            runTask1 <- system.runTaskFromTuple(node)(task1)
            runTask2 <- system.runTaskFromTuple(node)(task2)
            pausedTasks <- (system.savePauseTask(runTask1), system.savePauseTask(runTask2)).parTupled.delayBy(2.seconds)
            updatedTasks <- (system.updateCpus(pausedTasks._1, cpuSet1), system.updateCpus(pausedTasks._2, cpuSet2)).parTupled
            resumedTasks <- (system.saveResumeTask(updatedTasks._1), system.saveResumeTask(updatedTasks._2)).parTupled
            pausedTasks2 <- (system.savePauseTask(resumedTasks._1), system.savePauseTask(resumedTasks._2)).parTupled.delayBy(2.seconds)
            updatedTasks2 <- (system.updateCpus(pausedTasks2._1, cpuSet2), system.updateCpus(pausedTasks2._2, cpuSet1)).parTupled
            resumedTasks2 <- (system.saveResumeTask(updatedTasks2._1), system.saveResumeTask(updatedTasks2._2)).parTupled
            speedsMeasures <- (system.speedOf(measureDuration)(resumedTasks2._1), system.speedOf(measureDuration)(resumedTasks2._2)).parTupled.delayBy(delay)
            wait <- (system.waitForTask(resumedTasks2._1), system.waitForTask(resumedTasks2._2)).parTupled.withTime
            _ <- log.info("")(s"stupid time: ${wait._2}\ntask1.speed = ${speedsMeasures._1}\ntask2.speed = ${speedsMeasures._2}")
        } yield ()

        val seqRun = for {
            node <- system.nodeInfo(nodeUri)
            runTask1 <- system.runTaskFromTuple(node)(task1)
            wait1 <- system.waitForTask(runTask1).withTime
            runTask2 <- system.runTaskFromTuple(node)(task2)
            wait2 <- system.waitForTask(runTask2).withTime
            _ <- log.info("")(s"task1.time = ${wait1._2}; task2.time = ${wait2._2}")
        } yield ()

        sameRun >> ExitCode.Success.pure[IO]
    }
}
