//
// Sbt es 'enrevesado'. Para poder tocar y entenderlo bien hace falta leerse el tutorial completo!
//
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
    publishArtifact in (Compile, packageDoc) := false,

    // Quitamos la carpeta "assets" de todos los subprojectos
    resourceDirectory in Compile := baseDirectory.value / "app",

    // Para que la compilacion incremental sea mas rapida. De momento dicen que es experimental con sbt 0.13.7
    // http://typesafe.com/blog/improved-dependency-management-with-sbt-0137
    updateOptions := updateOptions.value.withCachedResolution(true)
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

  // Hacemos el hook de las rutas hijas (por ejemplo, admin/) dentro del fichero de routas ('backend.routes'), como
  // esta documentado que hay que hacerlo, con la sintaxis de flecha "-> /admin admin.routes". Esto genera un warning
  // sobre que debemos activar las reflectiveCalls en Scala. Pero como no sabemos las implicaciones de hacer esto,
  // preferimos dejarlo sin activar hasta que investiguemos mas y que siga saltando el warning.
  //scalacOptions ++= { Seq("-feature", "-language:reflectiveCalls") }
}