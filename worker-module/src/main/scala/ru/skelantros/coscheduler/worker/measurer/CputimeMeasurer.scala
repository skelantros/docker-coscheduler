package ru.skelantros.coscheduler.worker.measurer

import cats.effect.IO
import cats.implicits._

import scala.concurrent.duration.FiniteDuration
import scala.sys.process._

object CputimeMeasurer {
    private val cpuRegex = """user_usec\s+(\d+)""".r

    def cpuStatsFile(containerId: String) = s"/sys/fs/cgroup/system.slice/docker-$containerId.scope/cpu.stat"

    def cpu(containerId: String): Option[Long] =
        Seq("cat", cpuStatsFile(containerId)).!!.split("\n").collectFirst {
            case cpuRegex(cpuStr) => cpuStr.toLongOption
        }.flatten

    def apply(measureTime: FiniteDuration)(containerId: String): IO[Option[Double]] = for {
        startTime <- IO.monotonic
        cpuStart <- IO(cpu(containerId))
        _ <- IO.unit.delayBy(measureTime)
        cpuEnd <- IO(cpu(containerId))
        endTime <- IO.monotonic
        cpuDiff = (cpuEnd, cpuStart).mapN(_ - _).map(_.toDouble)
        timeDiff = (endTime - startTime).toMillis
        result = cpuDiff.map(_ / timeDiff)
    } yield result
}
