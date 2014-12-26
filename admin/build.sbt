name := "admin"

version := "1.0.0"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache
  ,filters
  ,javaWs
  ,"com.google.gdata" % "core" % "1.47.1"
  ,"org.apache.oltu.oauth2" % "org.apache.oltu.oauth2.client" % "1.0.0"
)