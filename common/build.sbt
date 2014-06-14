name := "common"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.mongodb" % "mongo-java-driver" % "2.12.0",
  "org.jongo" % "jongo" % "1.0"
)

play.Project.playJavaSettings