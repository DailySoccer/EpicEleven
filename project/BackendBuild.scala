import play.PlayJava
import sbt._
import Keys._

import java.io.PrintWriter
import scala.io.Source

object BackendBuild extends Build {

  lazy val removeAdminFromRouter = taskKey[Unit]("Removes the admin route from the router")

  val removeAdminFromRouterTask = removeAdminFromRouter := {

    println("Removing admin.Routes from conf/backend.routes")

    val fileName = "conf/backend.routes";
    val outLines = Source.fromFile(fileName).getLines().filter(line => {!line.contains("admin.Routes")}).toList
    val out = new PrintWriter(fileName)

    try {
      outLines.foreach(line => {out.println(line)})
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