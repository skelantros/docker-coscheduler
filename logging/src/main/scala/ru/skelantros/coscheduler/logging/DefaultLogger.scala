package ru.skelantros.coscheduler.logging

trait DefaultLogger extends Logger {
    override lazy val loggerName: String = getClass.getSimpleName
}
