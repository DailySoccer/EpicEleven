name := "admin"

version := "1.0-SNAPSHOT"

play.Project.playJavaSettings

libraryDependencies ++= Seq(
  cache,
  filters
)