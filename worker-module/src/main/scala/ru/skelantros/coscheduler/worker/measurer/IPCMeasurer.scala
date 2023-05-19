package ru.skelantros.coscheduler.worker.measurer

import cats.effect.IO
import ru.skelantros.coscheduler.model.CpuSet

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration
import scala.sys.process._

object IPCMeasurer {
    private val ipcRegex = """^.*instructions\s*#\s*([\d,]+).*$""".r

    // по каким-то причинам !! возвращает пустую строку для перфа
    private def runCmd(cmd: Seq[String]): Seq[String] = {
        val buf = new ArrayBuffer[String]
        val logger = ProcessLogger(s => {
            buf += s
        })
        cmd !!< logger
        buf.toSeq
    }

    private def measureIPC(cpuSet: Option[CpuSet], time: FiniteDuration): Option[Double] = {
        val coresFlags = cpuSet.fold("-a" :: Nil)(cs => "-C" :: cs.asString :: Nil)
        // perf stat -B -a dd if=/dev/zero of=/dev/null count=1000000
        val cmd = "perf" :: "stat" :: "-B" :: coresFlags ::: "dd" :: "if=/dev/zero" :: "of=/dev/null" :: s"count=${time.toMicros}" :: Nil

        runCmd(cmd).collectFirst {
            case ipcRegex(ipcStr) => ipcStr.replaceAll(",", ".").toDoubleOption
        }.flatten
    }

    def apply(duration: FiniteDuration)(cpuSet: Option[CpuSet]): IO[Option[Double]] = IO(measureIPC(cpuSet, duration))
}
