import play.PlayJava
import sbt._
import Keys._

import java.io.PrintWriter
import scala.io.Source

object BackendBuild extends Build {

  lazy val removeAdminFromRouter = taskKey[Unit]("Removes the admin route from the router")

  val removeAdminFromRouterTask = removeAdminFromRouter := {

    println("Removing admin.Routes from conf/backend.routes")

    var outLines : List[String] = List()
    val file = Source.fromFile("conf/backend.routes")

    file.getLines().foreach { line =>
      if (!line.contains("admin.Routes")) {
        outLines ::= line
      }
    }
    file.close()

    val out = new PrintWriter("conf/backend.routes")
    try {
      outLines.reverse.foreach(line => out.println(line))
    }
    finally {
      out.close()
    }
  }

  override lazy val projects = super.projects ++ backendProjects

  def backendProjects : Seq[sbt.Project] = {

    val common = Project(id = "common",
                         base = file("./common"))
                 .enablePlugins(PlayJava)

    if (file("./admin").exists()) {
      val admin = Project(id = "admin",
                          base = file("./admin"))
                 .dependsOn(common)
                 .enablePlugins(PlayJava)

      val backend = Project(id = "backend",
                            base = file("."))
                    .aggregate(common, admin)
                    .dependsOn(common, admin)
                    .enablePlugins(PlayJava)

      Seq(common, admin, backend)
    }
    else {
      val backend = Project(id = "backend",
                            base = file("."),
                            settings = Seq(removeAdminFromRouterTask,
                                           compile in Compile <<= (compile in Compile).dependsOn(removeAdminFromRouter)))
                    .aggregate(common)
                    .dependsOn(common)
                    .enablePlugins(PlayJava)

      Seq(common, backend)
    }
  }
}