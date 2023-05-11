package ru.skelantros.coscheduler.main.system

import cats.effect.IO
import ru.skelantros.coscheduler.model.Node
import cats.implicits._

trait WithMmbwmon { this: SchedulingSystem =>
    def mmbwmon(node: Node): IO[Double]

    private val avg: Iterable[Double] => Double = ns => ns.sum / ns.size

    // TODO не очень эффективно с точки зрения арифметики чисел с плавающей точки
    // TODO parSequence запускает запросы параллельно, но mmbwmon всё равно обрабатывает их последовательно, можно заменить на sequence
    def avgMmbwmon(node: Node)(attempts: Int): IO[Double] =
        (1 to attempts).map(_ => mmbwmon(node)).toList.parSequence.map(avg)
}
