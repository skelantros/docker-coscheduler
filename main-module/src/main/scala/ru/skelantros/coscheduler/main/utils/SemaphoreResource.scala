package ru.skelantros.coscheduler.main.utils

import cats.Applicative
import cats.effect.GenConcurrent
import cats.effect.std.Semaphore
import cats.implicits._

case class SemaphoreResource[A, F[_]](x: A, semaphore: Semaphore[F])(implicit conc: GenConcurrent[F, _]) {
    def getAndComputeF[B](f: A => F[B]): F[B] = for {
        _ <- semaphore.acquire
        action <- f(x)
        _ <- semaphore.release
    } yield action

    def getAndCompute[B](f: A => B): F[B] = getAndComputeF(f andThen Applicative[F].pure)
}

object SemaphoreResource {
    class Partial[F[_]](implicit conc: GenConcurrent[F, _]) {
        def from[A](x: A, availability: Int): F[SemaphoreResource[A, F]] = for {
            semaphore <- Semaphore[F](availability)
        } yield SemaphoreResource(x, semaphore)
    }

    def apply[F[_]](implicit conc: GenConcurrent[F, _]): Partial[F] = new Partial
}