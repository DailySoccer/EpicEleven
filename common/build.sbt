name := "common"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.mongodb" % "mongo-java-driver" % "2.12.0",
  "org.jongo" % "jongo" % "1.0",
  "org.jdom" % "jdom" % "2.0.2",
  javaJdbc,
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "commons-dbutils" % "commons-dbutils" % "1.6"     // http://commons.apache.org/proper/commons-dbutils/index.html
)

play.Project.playJavaSettings

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

//javacOptions := List("-encoding", "utf-8", "-Xlint:unchecked")
