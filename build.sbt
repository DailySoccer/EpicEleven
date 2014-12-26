name := "backend"

libraryDependencies ++= Seq(
  cache
  ,filters
  ,javaJdbc
  ,javaWs
  ,"com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC2"
  ,"com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC2"
)