name := "backend"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.mongodb" % "mongo-java-driver" % "2.12.0"    // https://github.com/mongodb/mongo-java-driver
  // "org.mongojack" % "mongojack" % "2.0.0"
)

play.Project.playJavaSettings
