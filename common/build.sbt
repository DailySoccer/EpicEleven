name := "common"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.mongodb" % "mongo-java-driver" % "2.12.0",
  "org.jongo" % "jongo" % "1.0",
  "com.typesafe.play" %% "play" % play.core.PlayVersion.current
)

// Solo somos una libreria, evitamos incluir el aparato completo del framework
// play.Project.playJavaSettings

// Pero no queremos las carpetas por defecto de sbt, definimos las nuestras con un solo root
sourceDirectory in Compile := baseDirectory.value / "src"

sourceDirectory in Test := baseDirectory.value / "test"

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test"

javaSource in Compile := baseDirectory.value / "src"

javaSource in Test := baseDirectory.value / "test"

resourceDirectory in Compile := baseDirectory.value / "conf"

resourceDirectory in Test := baseDirectory.value / "conf"

// Evita la creacion de la carpeta conf si no existe
unmanagedResourceDirectories in Compile ~= { _.filter(_.exists) }

unmanagedResourceDirectories in Test ~= { _.filter(_.exists) }

// Necesitamos acentos
javacOptions in (Compile, doc) := List("-encoding", "utf8", "-g")