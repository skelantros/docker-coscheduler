package ru.skelantros.coscheduler.main

import cats.{Monad, Traverse, UnorderedTraverse}
import cats.effect.IO
import cats.effect.kernel.Clock
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

object implicits {
    implicit class ParMapOps[A, CC[_] : Traverse](collection: CC[A]) {
        def parMap[B](f: A => IO[B]): IO[CC[B]] = collection.map(f).parSequence
    }

    implicit class GenMapOps[A, CC[_] : UnorderedTraverse](collection: CC[A]) {
        def genParMap[B, CC1[_] : Traverse](convert: CC[A] => CC1[A])(f: A => IO[B]): IO[CC1[B]] = convert(collection).parMap(f)
    }

    implicit class TimeMeasureOps[F[_]: Clock: Monad, A](action: F[A]) {
        def withTime: F[(A, FiniteDuration)] = for {
            start <- Clock[F].monotonic
            result <- action
            end <- Clock[F].monotonic
        } yield (result, end - start)
    }
}
