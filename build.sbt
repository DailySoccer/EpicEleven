name := "backend"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.mongodb" % "mongo-java-driver" % "2.12.0",    // https://github.com/mongodb/mongo-java-driver
  "org.jongo" % "jongo" % "1.0",                     // http://jongo.org/
  "org.seleniumhq.selenium" % "selenium-java" % "2.41.0" % "test",
  "org.scalatestplus" % "play_2.10" % "1.0.0" % "test",
  "com.saucelabs" % "sauce_junit" % "2.0.5" % "test",
  "com.fasterxml" % "aalto-xml" % "0.9.6",
  "org.codehaus.woodstox" % "stax2-api" % "3.1.4"
  // "org.mongojack" % "mongojack" % "2.0.0"
)

resolvers += "saucelabs-repository" at "http://repository-saucelabs.forge.cloudbees.com/release"

play.Project.playJavaSettings
