name := "admin"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  filters
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false