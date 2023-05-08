package ru.skelantros.coscheduler.main

import cats.{Traverse, UnorderedTraverse}
import cats.effect.IO
import cats.implicits._

object implicits {
    implicit class ParMapOps[A, CC[_] : Traverse](collection: CC[A]) {
        def parMap[B](f: A => IO[B]): IO[CC[B]] = collection.map(f).parSequence
    }

    implicit class GenMapOps[A, CC[_] : UnorderedTraverse](collection: CC[A]) {
        def genParMap[B, CC1[_] : Traverse](convert: CC[A] => CC1[A])(f: A => IO[B]): IO[CC1[B]] = convert(collection).parMap(f)
    }
}
