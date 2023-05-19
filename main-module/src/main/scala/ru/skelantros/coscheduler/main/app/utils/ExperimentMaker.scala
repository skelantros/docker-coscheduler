//package ru.skelantros.coscheduler.main.app.utils
//
//import cats.effect.{ExitCode, IO, IOApp}
//import ru.skelantros.coscheduler.main.app.WithConfigLoad
//import ru.skelantros.coscheduler.main.strategy.Strategy.StrategyTask
//
//import scala.util.Random
//
//object ExperimentMaker extends App with WithConfigLoad {
//    val config = loadConfiguration(args.toList).get.makeExperiment.get
//    val tasks = config.tasks
//    val random = new Random
//
//    val shuffledTasks = (0 until config.count).map(i => s"comb$i" -> random.shuffle(tasks))
//
//    def tabsStr(count: Int) = " " * (spacesTab * count)
//
//    val spacesTab = 4
//
//    def printComb(title: String, comb: Vector[StrategyTask], tabs: Int): String =
//        s"""${tabsStr(tabs)}title = $title
//           |${tabsStr(tabs)}tasks = [
//           |${comb.map(st => s"${tabsStr(tabs + 1)}{ title = ${st.title}, dir = ${st.dir}, all-cores-dir = ${st.allCoresDir} }").mkString(",\n")}""".stripMargin
//
//    def printCombs(combinations: Seq[(String, Vector[StrategyTask])], tabs: Int): String =
//        s"""${tabsStr(tabs)}combinations [
//           |${combinations.map { case (t, comb) => printComb(t, comb, tabs + 1) }.mkString("\n")}
//           |]""".stripMargin
//
//    def printTestCases(title: String, combTitle: String, tabs: Int): String =
//        s"title = $title" ::
//            s"attempts = 1" ::
//            s"strategies = {"
//            s"${tabsStr}"
//
//    s"""combinations {
//       |${shuffledTasks.map { case (title, comb)}
//       |}
//       |""".stripMargin
//}
