package ru.skelantros.coscheduler.main.app.utils

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toTraverseOps
import ru.skelantros.coscheduler.logging.{DefaultLogger, Logger}
import ru.skelantros.coscheduler.main.Experiment
import ru.skelantros.coscheduler.main.Experiment.{TestCase, TestCaseStrategies}
import ru.skelantros.coscheduler.main.app.WithConfigLoad
import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
import ru.skelantros.coscheduler.main.strategy.parallel.FCStrategy
import ru.skelantros.coscheduler.main.strategy.{FCSHybridStrategy, MemoryBWAltStrategy, SequentialStrategy, Strategy}
import ru.skelantros.coscheduler.main.system.HttpSchedulingSystem

import java.io.File
import scala.concurrent.duration.Duration

object ExperimentApp extends IOApp with WithConfigLoad with DefaultLogger {
    override def loggerConfig: Logger.Config = Logger.defaultConfig

    private case class ParsedTestCase(title: String, attempts: Int, strategies: TestCaseStrategies, combination: Vector[StrategyTask], randomize: Boolean)
    private def parsedTestCase(testCase: TestCase, combination: Vector[StrategyTask]): ParsedTestCase =
        ParsedTestCase(testCase.title, testCase.attempts, testCase.strategies, combination, testCase.randomize)

    private def checkDockerfiles(experiment: Experiment): Unit = {
        val badFolders = experiment.combinations.flatMap(_.tasks).flatMap(x => Vector(x.dir) ++ x.allCoresDir).collect {
            case f if !(new File(f, "Dockerfile").isFile) => f
        }
        if(badFolders.nonEmpty) throw new Exception(s"Next tasks don't have Dockerfiles:\n${badFolders.mkString("\n")}")
    }

    private def parseTestCases(experiment: Experiment): Seq[ParsedTestCase] = {
        checkDockerfiles(experiment)
        val combinationsMap = experiment.combinations.map(x => (x.name, x.tasks)).toMap
        experiment.testCases.map(tc => parsedTestCase(tc, combinationsMap(tc.combination)))
    }

    private def singleRun(strategy: Strategy, testCase: ParsedTestCase, attempt: Int, strategyTitleOpt: Option[String] = None)(combination: Vector[StrategyTask]): IO[Unit] = {
        val sTitle = strategyTitleOpt.getOrElse(strategy.getClass.getSimpleName)

        for {
            result <- strategy.executeWithInit(combination)
            totalTime = result.totalTime
            preStageTime = result.preStageTime.getOrElse(Duration.fromNanos(0))
            executeTime = totalTime - preStageTime
            _ <- log.info("")(s"${combination.map(_.title).mkString(";")}\t${testCase.title}\t$attempt\t$sTitle\t$preStageTime\t$executeTime\t$totalTime")
        } yield ()
    }

    private def shuffle[A](combination: Vector[A]): Vector[A] =
        new scala.util.Random().shuffle(combination)

    private def singleAttempt(fcs: FCStrategy, fcsHybrid: FCSHybridStrategy, bw: MemoryBWAltStrategy, seq: SequentialStrategy)(testCase: ParsedTestCase, attempt: Int): IO[Unit] = {
        import testCase.strategies

        for {
            comb <- IO(if(testCase.randomize) shuffle(testCase.combination) else testCase.combination)
            _ <- if(strategies.fcs) singleRun(fcs, testCase, attempt)(comb) else IO.unit
            _ <- if(strategies.fcsHybrid) singleRun(fcsHybrid, testCase, attempt)(comb) else IO.unit
            _ <- if(strategies.bw) singleRun(bw, testCase, attempt)(comb) else IO.unit
            _ <- if(strategies.seq) singleRun(seq, testCase, attempt, Some("seq"))(comb) else IO.unit
            _ <- if(strategies.seqAll) singleRun(seq, testCase, attempt, Some("seqAll"))(comb.map(_.toAllCores)) else IO.unit
        } yield ()
    }.handleErrorWith { t => log.error("")(s"Attempt $attempt of test case ${testCase.title} failed with error $t:\n${t.getStackTrace.mkString("\n")}")}

    private def runTestCase(fcs: FCStrategy, fcsHybrid: FCSHybridStrategy, bw: MemoryBWAltStrategy, seq: SequentialStrategy)(testCase: ParsedTestCase): IO[Unit] =
        (1 to testCase.attempts).map(singleAttempt(fcs, fcsHybrid, bw, seq)(testCase, _)).toList.sequence >> IO.unit

    override def run(args: List[String]): IO[ExitCode] = {
        val commonConfig = loadConfiguration(args).get
        val experiment = commonConfig.experiment.get
        val testCases = parseTestCases(experiment)

        val schedulingSystem = HttpSchedulingSystem.withLogging(commonConfig)

        val fcs = FCStrategy(schedulingSystem, commonConfig)
        val fcsHybrid = FCSHybridStrategy(schedulingSystem, commonConfig)
        val bw = MemoryBWAltStrategy(schedulingSystem, commonConfig)
        val seq = SequentialStrategy(schedulingSystem, commonConfig)

        testCases.map(runTestCase(fcs, fcsHybrid, bw, seq)).sequence >> IO.pure(ExitCode.Success)
    }
}
