name := "admin"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  filters,
  javaWs
  ,"com.google.gdata" % "core" % "1.47.1"
  ,"org.apache.oltu.oauth2" % "org.apache.oltu.oauth2.client" % "1.0.0"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false