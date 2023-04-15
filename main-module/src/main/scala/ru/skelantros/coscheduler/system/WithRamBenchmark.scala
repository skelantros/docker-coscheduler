package ru.skelantros.coscheduler.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.Node

trait WithRamBenchmark { this: SchedulingSystem =>
    def ramBenchmark(node: Node): IO[Double]
}
