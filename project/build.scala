//
// Sbt es 'enrevesado'. Para poder tocar y entenderlo bien hace falta leerse el tutorial completo!
//
import play.PlayImport.PlayKeys._
import play.PlayJava
import sbt._
import sbt.Keys._
import play.Play.autoImport._
import com.typesafe.sbt.web.SbtWeb.autoImport._

import java.io.PrintWriter
import scala.io.Source

object build extends Build {

  lazy val commonSettings = Seq(
    version := "1.0.0",
    scalaVersion := "2.11.1",

    // Desconectamos la compilacion de documentacion, que nos ralentiza el deploy
    sources in (Compile,doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,

    sourceDirectory in Assets := (sourceDirectory in Compile).value,

    // Para que la compilacion incremental sea mas rapida. De momento dicen que es experimental con sbt 0.13.7
    // http://typesafe.com/blog/improved-dependency-management-with-sbt-0137
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

  lazy val removeAdminFromRouterTaskKey = taskKey[Unit]("Removes the admin route from the router")

  val removeAdminFromRouterSetting = removeAdminFromRouterTaskKey := {

    println("Removing admin.Routes from conf/backend.routes")

    val fileName = "conf/backend.routes"
    val inLines  = Source.fromFile(fileName).getLines().toList
    val outLines = inLines.filter(line => {!line.contains("admin.Routes")}).toList

    if (outLines.length != inLines.length) {
      val out = new PrintWriter(fileName)

      try {
        outLines.foreach(line => {
          out.println(line)
        })
      }
      finally {
        out.close()
      }
    }
  }

  override lazy val projects = super.projects ++ backendProjects

  def backendProjects : Seq[sbt.Project] = {

    val common = Project(id = "common",
                         base = file("./common"))
                .settings(commonSettings:_*)
                .settings(libraryDependencies ++= Seq(
                    javaJdbc
                    ,javaWs
                    ,"org.mongodb" % "mongo-java-driver" % "3.4.0"
                    ,"org.jongo" % "jongo" % "1.3.0"
                    ,"org.jdom" % "jdom" % "2.0.2"
                    ,"org.joda" % "joda-money" % "0.10.0"
                    ,"postgresql" % "postgresql" % "9.1-901-1.jdbc4"
                    ,"commons-dbutils" % "commons-dbutils" % "1.6"           // http://commons.apache.org/proper/commons-dbutils/index.html
                    ,"org.jooq" % "jooq" % "3.5.0"
                    ,"org.jooq" % "jooq-meta" % "3.5.0"
                    ,"org.flywaydb" % "flyway-core" % "3.1"
                    ,"com.paypal.sdk" % "paypal-core" % "1.7.0"
                    ,"com.paypal.sdk" % "rest-api-sdk" % "1.4.1"
                    ,"com.rabbitmq" % "amqp-client" % "3.4.3"
                    ,"org.ejml" % "all" % "0.28"
                    ,"org.apache.commons" % "commons-math3" % "3.5"
                  ))
                .settings(resourceDirectory in Assets := baseDirectory.value / "app")
                .enablePlugins(PlayJava)

    var backend = Project(id = "backend",
                          base = file("."))
                  .settings(commonSettings:_*)
                  .settings(libraryDependencies ++= Seq(
                     cache
                    ,filters
                    ,javaJdbc
                    ,javaWs
                    ,"com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC3"
                    ,"com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC3"
                    ,"com.paypal.sdk" % "paypal-core" % "1.7.0"
                    ,"com.paypal.sdk" % "rest-api-sdk" % "1.4.1"
                    ,"com.github.ben-manes.caffeine" % "caffeine" % "2.3.5"
                  ))
                 .enablePlugins(PlayJava)
                 .aggregate(common)
                 .dependsOn(common)

    if (file("./admin").exists()) {
      val admin = Project(id = "admin",
                          base = file("./admin"))
                 .settings(commonSettings:_*)
                 .settings(libraryDependencies ++= Seq(
                    cache
                   ,filters
                   ,javaWs
                   ,"org.apache.poi" % "poi-ooxml" % "3.11"
                 ))
                .enablePlugins(PlayJava)
                .dependsOn(common)

      backend = backend.aggregate(admin)
                       .dependsOn(admin)

      Seq(common, admin, backend)
    }
    else {
      backend = backend.settings(removeAdminFromRouterSetting,
                                 compile in Compile <<= (compile in Compile).dependsOn(removeAdminFromRouterTaskKey))

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