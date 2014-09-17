name := "admin"

version := "1.0-SNAPSHOT"

play.Project.playJavaSettings

libraryDependencies ++= Seq(
  cache,
  filters
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false