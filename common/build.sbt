name := "common"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  javaJdbc
  ,"org.mongodb" % "mongo-java-driver" % "2.12.3"
  ,"org.jongo" % "jongo" % "1.0"
  ,"org.jdom" % "jdom" % "2.0.2"
  ,"postgresql" % "postgresql" % "9.1-901-1.jdbc4"
  ,"commons-dbutils" % "commons-dbutils" % "1.6"           // http://commons.apache.org/proper/commons-dbutils/index.html
  ,"org.jooq" % "jooq" % "3.5.0"
  ,"org.jooq" % "jooq-meta" % "3.5.0"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

//javacOptions := List("-encoding", "utf-8", "-Xlint:unchecked")
