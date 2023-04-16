ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val cats = "org.typelevel" %% "cats-core" % "2.9.0"
lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.4.8"
lazy val fs2 = "co.fs2" %% "fs2-core" % "3.6.1"
lazy val dockerClient = "com.spotify" % "docker-client" % "8.16.0"
lazy val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.17.2"

lazy val tapirVersion = "1.2.12"
lazy val tapirDeps = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-enumeratum" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % tapirVersion
)

lazy val sttpClientDeps = Seq(
    "com.softwaremill.sttp.client3" %% "fs2" % "3.8.15",
    "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
    "com.softwaremill.sttp.client3" %% "http4s-backend" % "3.8.15"
)

lazy val http4sVersion = "0.23.18"
lazy val http4s = Seq(
    "org.http4s" %% "http4s-core" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-ember-client" % http4sVersion
)

lazy val logging = "org.slf4j" % "slf4j-simple" % "2.0.7"

lazy val root = (project in file("."))
  .settings(
    name := "docker-coscheduler"
  )

lazy val models = (project in file("models"))
    .settings(
        libraryDependencies ++= (tapirDeps :+ cats)
    )

lazy val mainModule = (project in file("main-module"))
    .dependsOn(models)
    .settings(
        libraryDependencies ++= Seq(cats, catsEffect)
    )

lazy val workerModule = (project in file("worker-module"))
    .dependsOn(models)
    .settings(
        libraryDependencies ++= (tapirDeps ++ http4s :+ dockerClient :+ logging)
    )

lazy val sandbox = (project in file("sandbox"))
    .dependsOn(models)
    .settings(
        libraryDependencies ++= sttpClientDeps ++ tapirDeps ++ http4s :+ dockerClient
    )