package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.Node
import cats.implicits._

trait WithRamBenchmark { this: SchedulingSystem =>
    def ramBenchmark(node: Node): IO[Double]

    private val avg: Iterable[Double] => Double = ns => ns.sum / ns.size

    // TODO не очень эффективно с точки зрения арифметики чисел с плавающей точки
    def avgRamBenchmark(node: Node)(attempts: Int): IO[Double] =
        (1 to attempts).map(_ => ramBenchmark(node)).toList.parSequence.map(avg)
}
