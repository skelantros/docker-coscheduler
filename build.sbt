ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val cats = "org.typelevel" %% "cats" % "2.9.0"
lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.4.8"
lazy val tapir = "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.2.12"
lazy val fs2 = "co.fs2" %% "fs2-core" % "3.6.1"
lazy val dockerClient = "com.spotify" % "docker-client" % "8.16.0"
lazy val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.17.2"

lazy val root = (project in file("."))
  .settings(
    name := "docker-coscheduler"
  )

lazy val models = (project in file("models"))

lazy val mainModule = (project in file("main-module"))

lazy val workerModule = (project in file("worker-module"))