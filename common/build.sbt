name := "common"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.mongodb" % "mongo-java-driver" % "2.12.0",
  "org.jongo" % "jongo" % "1.0",
  "org.jdom" % "jdom" % "2.0.2",
  javaJdbc,
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "com.typesafe.play" %% "play" % play.core.PlayVersion.current
)

// Solo somos una libreria, evitamos incluir el aparato completo del framework
// play.Project.playJavaSettings

// Pero no queremos las carpetas por defecto de sbt, definimos las nuestras con un solo root
sourceDirectory in Compile := baseDirectory.value / "app"

sourceDirectory in Test := baseDirectory.value / "test"

scalaSource in Compile := baseDirectory.value / "app"

scalaSource in Test := baseDirectory.value / "test"

javaSource in Compile := baseDirectory.value / "app"

javaSource in Test := baseDirectory.value / "test"

resourceDirectory in Compile := baseDirectory.value / "conf"

resourceDirectory in Test := baseDirectory.value / "conf"

// Evita la creacion de la carpeta conf si no existe
unmanagedResourceDirectories in Compile ~= { _.filter(_.exists) }

unmanagedResourceDirectories in Test ~= { _.filter(_.exists) }

// Necesitamos acentos
javacOptions := List("-encoding", "utf-8")