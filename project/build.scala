//
// Sbt es 'enrevesado'. Para poder tocar y entenderlo bien hace falta leerse el tutorial completo!
//
import play.PlayJava
import sbt._
import sbt.Keys._
import play.Play.autoImport._

import java.io.PrintWriter
import scala.io.Source

object build extends Build {

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
                .settings(libraryDependencies ++= Seq(
                     javaJdbc
                    ,"org.mongodb" % "mongo-java-driver" % "2.12.3"
                    ,"org.jongo" % "jongo" % "1.0"
                    ,"org.jdom" % "jdom" % "2.0.2"
                    ,"postgresql" % "postgresql" % "9.1-901-1.jdbc4"
                    ,"commons-dbutils" % "commons-dbutils" % "1.6"           // http://commons.apache.org/proper/commons-dbutils/index.html
                    ,"org.jooq" % "jooq" % "3.5.0"
                    ,"org.jooq" % "jooq-meta" % "3.5.0"
                    ,"org.flywaydb" % "flyway-core" % "3.1"
                  ))
                // No queremos las carpetas por defecto de sbt (este no es un PlayProject), definimos las nuestras con un solo root.
                // Por no ser un proyecto play, hay que definir a mano el encoding
                .settings(sourceDirectory in Compile := baseDirectory.value / "app",
                          scalaSource in Compile := baseDirectory.value / "app",
                          javaSource in Compile := baseDirectory.value / "app",
                          javacOptions := List("-encoding", "utf-8"))

    var backend = Project(id = "backend",
                          base = file("."))
                  .enablePlugins(PlayJava)
                  .settings(commonSettings:_*)
                  .settings(libraryDependencies ++= Seq(
                     cache
                    ,filters
                    ,javaJdbc
                    ,javaWs
                    ,"com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC2"
                    ,"com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC2"
                  ))
                 .aggregate(common)
                 .dependsOn(common)

    if (file("./admin").exists()) {
      val admin = Project(id = "admin",
                          base = file("./admin"))
                 .dependsOn(common)
                 .enablePlugins(PlayJava)
                 .settings(commonSettings:_*)
                 .settings(libraryDependencies ++= Seq(
                    cache
                   ,filters
                   ,javaWs
                   ,"com.google.gdata" % "core" % "1.47.1"
                   ,"org.apache.oltu.oauth2" % "org.apache.oltu.oauth2.client" % "1.0.0"
                 ))

      backend = backend.aggregate(admin)
                       .dependsOn(admin)

      Seq(common, admin, backend)
    }
    else {
      backend = backend.settings(removeAdminFromRouterTask,
                                 compile in Compile <<= (compile in Compile).dependsOn(removeAdminFromRouter))

      Seq(common, backend)
    }
  }

  // javacOptions := List("-Xlint:unchecked")

  // Hacemos el hook de las rutas hijas (por ejemplo, admin/) dentro del fichero de routas ('backend.routes'), como
  // esta documentado que hay que hacerlo, con la sintaxis de flecha "-> /admin admin.routes". Esto genera un warning
  // sobre que debemos activar las reflectiveCalls en Scala. Pero como no sabemos las implicaciones de hacer esto,
  // preferimos dejarlo sin activar hasta que investiguemos mas y que siga saltando el warning.
  //scalacOptions ++= { Seq("-feature", "-language:reflectiveCalls") }
}