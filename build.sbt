name := "backend"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.mongodb" % "mongo-java-driver" % "2.12.0",    // https://github.com/mongodb/mongo-java-driver
  "org.jongo" % "jongo" % "1.0",                     // http://jongo.org/
  // "org.apache.httpcomponents" % "httpclient" % "4.3.1",
  "org.seleniumhq.selenium" % "selenium-java" % "2.41.0" % "test"
  // "org.scalatest" % "scalatest_2.11" % "2.1.4" % "test"
  // "org.mongojack" % "mongojack" % "2.0.0"
)

play.Project.playJavaSettings
