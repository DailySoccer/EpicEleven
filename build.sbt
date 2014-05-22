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
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13",
  "com.fasterxml" % "jackson-xml-databind" % "0.6.2",
  "org.json" % "json" % "20140107"
  // "org.mongojack" % "mongojack" % "2.0.0"
)

resolvers += "saucelabs-repository" at "http://repository-saucelabs.forge.cloudbees.com/release"

play.Project.playJavaSettings
