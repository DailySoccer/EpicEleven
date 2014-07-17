name := "common"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.mongodb" % "mongo-java-driver" % "2.12.0",
  "org.jongo" % "jongo" % "1.0",
  "org.jdom" % "jdom" % "2.0.2",
  javaJdbc,
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4"
)

play.Project.playJavaSettings

//javacOptions := List("-encoding", "utf-8", "-Xlint:unchecked")
