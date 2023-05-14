package ru.skelantros.coscheduler.main.app.utils

import cats.effect.{ExitCode, IO, IOApp}
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.Configuration.TasksTestParams
import ru.skelantros.coscheduler.main.app.WithConfigLoad
import ru.skelantros.coscheduler.main.system.{EndpointException, HttpSchedulingSystem}
import ru.skelantros.coscheduler.model.{CpuSet, Node, Task}
import cats.implicits._
import ru.skelantros.coscheduler.main.implicits.TimeMeasureOps
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask

import scala.concurrent.duration.FiniteDuration

object TasksTest extends IOApp with WithConfigLoad with DefaultLogger {
    override def run(args: List[String]): IO[ExitCode] = {
        val config = loadConfiguration(args).get
        val schedulingSystem = HttpSchedulingSystem.withLogging(config)
        val tasksTestConfig = config.tasksTest.get

        def mmbwmonMeasure(task: Task.Created, node: Node, attempts: Int)(currentMeasures: List[Double]): IO[List[Double]] = for {
            result <- schedulingSystem.avgMmbwmon(node)(attempts)
            _ <- log.debug(task.title)(s"mmbwmon: $result")
            isRunning <- schedulingSystem.isRunning(task)
        } yield if(isRunning) result :: currentMeasures else currentMeasures

        def testMmbwmon(task: Task.Created, node: Node, params: TasksTestParams)(currentMeasures: List[Double]): IO[List[Double]] = for {
            isRunning <- schedulingSystem.isRunning(task)
            action <-
                if(isRunning) mmbwmonMeasure(task, node, params.attempts)(currentMeasures).flatMap(testMmbwmon(task, node, params)(_).delayBy(params.delay))
                else IO.pure(currentMeasures)
        } yield action


        def testSpeed(params: TasksTestParams, task: Task.Created)(measures: List[Double]): IO[List[Double]] =
            schedulingSystem.speedOf(params.time)(task).flatMap(m =>
                log.debug(task.title)(s"speed: $m") >> IO.sleep(params.delay) >> testSpeed(params, task)(m :: measures)
            ).recoverWith {
                case EndpointException(t) => log.debug(task.title)(s"Measurement completed with: $t") >> IO.pure(measures)
            }

        def singleTaskResults(task: Task.Created): IO[(Results, Results, FiniteDuration)] = for {
            speedResultsT <- tasksTestConfig.speedParams.fold(IO.pure(List.empty[Double]))(params => testSpeed(params, task)(List.empty)).withTime
            (speedResults, time) = speedResultsT
            mmbwmonResults <- tasksTestConfig.mmbwmon.fold(IO.pure(List.empty[Double]))(params => testMmbwmon(task, task.node, params)(List.empty))
            asc = (avgSdCount(mmbwmonResults), avgSdCount(speedResults), time)
        } yield asc

        def singleTask(node: Node)(strategyTask: StrategyTask): IO[Unit] = for {
            _ <- log.debug(strategyTask._1)(s"Starting task ${strategyTask._1}")
            runTask <- schedulingSystem.runTaskFromTuple(node)(strategyTask, CpuSet(1, node.cores - 1).some)
            results <- singleTaskResults(runTask)
            _ <- log.info(runTask.title)(s"${runTask.title}\t${results._1}\t${results._2}\t${results._3}")
        } yield ()

        for {
            nodeInfo <- schedulingSystem.nodeInfo(tasksTestConfig.nodeUri)
            _ <- log.info("")(s"task_title\tmmbwmon\ttask_speed\ttime")
            _ <- tasksTestConfig.tasks.map(singleTask(nodeInfo)).sequence
        } yield ExitCode.Success
    }


    override def loggerConfig: Logger.Config = Logger.Config(info = true, debug = true)

    private def avg(numbers: Iterable[Double]): Double = numbers.sum / numbers.size

    type Results = (Double, Double, Int)



    private def avgSdCount(numbers: Iterable[Double]): Results =
        if(numbers.nonEmpty) {
            val count = numbers.size
            val avgRes = avg(numbers)
            val disp = numbers.map(x => (x - avgRes) * (x - avgRes)).sum / count
            val sd = math.sqrt(disp)
            (avgRes, sd, count)
        } else (0, 0, 0)
}