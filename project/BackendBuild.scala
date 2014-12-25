import play.PlayJava
import sbt._
import sbt.Keys._

import java.io.PrintWriter
import scala.io.Source

object BackendBuild extends Build {

  lazy val commonSettings = Seq(
    version := "1.0.0",
    scalaVersion := "2.11.1",
    // Desconectamos la compilacion de documentacion, que nos ralentiza el deploy
    sources in (Compile,doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false
  )

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
                .settings(commonSettings:_*)

    if (file("./admin").exists()) {
      val admin = Project(id = "admin",
                          base = file("./admin"))
                 .dependsOn(common)
                 .enablePlugins(PlayJava)
                 .settings(commonSettings:_*)

      val backend = Project(id = "backend",
                            base = file("."))
                    .aggregate(common, admin)
                    .dependsOn(common, admin)
                    .enablePlugins(PlayJava)
                    .settings(commonSettings:_*)

      Seq(common, admin, backend)
    }
    else {
      val backend = Project(id = "backend",
                            base = file("."))
                    .aggregate(common)
                    .dependsOn(common)
                    .enablePlugins(PlayJava)
                    .settings(removeAdminFromRouterTask,
                              compile in Compile <<= (compile in Compile).dependsOn(removeAdminFromRouter))
                    .settings(commonSettings:_*)

      Seq(common, backend)
    }
  }
}