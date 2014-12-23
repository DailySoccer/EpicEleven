name := "admin"

libraryDependencies ++= Seq(
  cache
  ,filters
  ,javaWs
  ,"org.apache.poi" % "poi-ooxml" % "3.11"
  ,"com.google.gdata" % "core" % "1.47.1"
  ,"org.apache.oltu.oauth2" % "org.apache.oltu.oauth2.client" % "1.0.0"
)
