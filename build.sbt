name := "backend"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.mongodb" % "mongo-java-driver" % "2.12.0",    // https://github.com/mongodb/mongo-java-driver
  "org.jongo" % "jongo" % "1.0"                      // http://jongo.org/
  // "org.mongojack" % "mongojack" % "2.0.0"
)

play.Project.playJavaSettings
