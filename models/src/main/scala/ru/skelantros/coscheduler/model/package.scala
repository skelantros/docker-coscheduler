package ru.skelantros.coscheduler

package object model {
    type WorkerResponse[A] = Either[Throwable, A]
}
