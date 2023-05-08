package ru.skelantros.coscheduler.logging

import cats.Show
import cats.effect.IO
import cats.implicits.toShow

import java.sql.Timestamp

trait Logger {
    def loggerName: String
    def loggerConfig: Logger.Config

    object log {
        private def prefix[T : Show](tag: T): String =
            if(tag.show.isEmpty) loggerName else s"$loggerName-${tag.show}"

        private def print[T : Show, A : Show](tag: T, msgType: String)(content: =>A)(print: String => IO[Unit]): IO[Unit] = for {
            instant <- IO.realTime
            timestamp = new Timestamp(instant.toMillis)
            _ <- print(s"[${prefix(tag)}] $msgType $timestamp: ${content.show}")
        } yield ()

        def info[T : Show, A : Show](tag: T)(content: =>A): IO[Unit] =
            if(loggerConfig.info) print(tag, "INFO")(content)(IO.println) else IO.unit
        def debug[T : Show, A : Show](tag: T)(content: =>A): IO[Unit] =
            if(loggerConfig.debug) print(tag, "DEBUG")(content)(IO.println) else IO.unit
        def error[T : Show, A : Show](tag: T)(content: =>A): IO[Unit] =
            if(loggerConfig.error) print(tag, "ERROR")(content)(x => IO(System.err.println(x))) else IO.unit
    }
}

object Logger {
    case class Config(info: Boolean = true,
                      debug: Boolean = true,
                      error: Boolean = true)

    val defaultConfig: Config = Config()
}