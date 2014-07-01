//
// Sbt es enrevesado. Para poder tocar y entenderlo bien hace falta leerse el tutorial completo!
//
// Todas las keys: http://www.scala-sbt.org/0.13/sxr/sbt/Keys.scala.html
// Y sus defaults: http://www.scala-sbt.org/0.13/sxr/sbt/Defaults.scala.html

name := "backend"

version := "1.0-SNAPSHOT"

play.Project.playJavaSettings

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.mongodb" % "mongo-java-driver" % "2.12.0",                  // https://github.com/mongodb/mongo-java-driver
  "org.jongo" % "jongo" % "1.0",                                   // http://jongo.org/
  "org.json" % "json" % "20140107",
  javaJdbc,
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "org.seleniumhq.selenium" % "selenium-java" % "2.41.0" % "test",
  "org.scalatestplus" % "play_2.10" % "1.0.0" % "test",
  "com.saucelabs" % "sauce_junit" % "2.0.5" % "test"
)

resolvers += "saucelabs-repository" at "http://repository-saucelabs.forge.cloudbees.com/release"

// Definimos nuestros projectos y las dependencias entre ellos
lazy val common = project

lazy val admin = project.dependsOn(common)

lazy val backend = project.in(file(".")).aggregate(common, admin).dependsOn(common, admin)