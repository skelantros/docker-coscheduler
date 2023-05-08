package ru.skelantros.coscheduler.ledger

import cats.effect.Async
import doobie.util.transactor.Transactor

object LedgerTransactor {
    case class Config(host: String, port: Int, db: String, user: String, password: String)

    def driverManager[F[_]: Async](config: Config): Transactor[F] = Transactor.fromDriverManager(
        "org.postgresql.Driver",
        s"jdbc:postgresql://${config.host}:${config.port}/${config.db}",
        config.user,
        config.password
    )
}
