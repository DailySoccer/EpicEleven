//
// Sbt es enrevesado. Para poder tocar y entenderlo bien hace falta leerse el tutorial completo!
//
// Las siguientes URLs no apuntan a la ultima version desde el upgrade a Play 2.3.4:
//
// Todas las keys: http://www.scala-sbt.org/0.13/sxr/sbt/Keys.scala.html
// Y sus defaults: http://www.scala-sbt.org/0.13/sxr/sbt/Defaults.scala.html

name := "backend"

libraryDependencies ++= Seq(
  cache
  ,filters
  ,javaJdbc
  ,javaWs
  ,"com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC2"
  ,"com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC2"
)

// Para que la compilacion incremental sea mas rapida. De momento dicen que es experimental con sbt 0.13.7
// http://typesafe.com/blog/improved-dependency-management-with-sbt-0137
updateOptions := updateOptions.value.withCachedResolution(true)

// javacOptions ++= Seq("-Xlint:deprecation")

// Hacemos el hook de las rutas hijas (por ejemplo, admin/) dentro del fichero de routas ('backend.routes'), como
// esta documentado que hay que hacerlo, con la sintaxis de flecha "-> /admin admin.routes". Esto genera un warning
// sobre que debemos activar las reflectiveCalls en Scala. Pero como no sabemos las implicaciones de hacer esto,
// preferimos dejarlo sin activar hasta que investiguemos mas y que siga saltando el warning.
//scalacOptions ++= { Seq("-feature", "-language:reflectiveCalls") }
