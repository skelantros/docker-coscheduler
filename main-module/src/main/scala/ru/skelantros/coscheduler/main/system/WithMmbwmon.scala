package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.{CpuSet, Node}
import cats.implicits._

trait WithMmbwmon { this: SchedulingSystem =>
    def mmbwmon(node: Node, cpuSet: CpuSet): IO[Double]

    private val avg: Iterable[Double] => Double = ns => ns.sum / ns.size

    // TODO не очень эффективно с точки зрения арифметики чисел с плавающей точки
    // TODO parSequence запускает запросы параллельно, но mmbwmon всё равно обрабатывает их последовательно, можно заменить на sequence
    def avgMmbwmon(node: Node, cpuSet: CpuSet)(attempts: Int): IO[Double] =
        (1 to attempts).map(_ => mmbwmon(node, cpuSet)).toList.parSequence.map(avg)
}
