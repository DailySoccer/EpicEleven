name := "common"

version := "1.0.0"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  javaJdbc
  ,"org.mongodb" % "mongo-java-driver" % "2.12.3"
  ,"org.jongo" % "jongo" % "1.0"
  ,"org.jdom" % "jdom" % "2.0.2"
  ,"postgresql" % "postgresql" % "9.1-901-1.jdbc4"
  ,"commons-dbutils" % "commons-dbutils" % "1.6"           // http://commons.apache.org/proper/commons-dbutils/index.html
  ,"org.jooq" % "jooq" % "3.5.0"
  ,"org.jooq" % "jooq-meta" % "3.5.0"
  ,"org.flywaydb" % "flyway-core" % "3.1"
)

// No queremos las carpetas por defecto de sbt, definimos las nuestras con un solo root
sourceDirectory in Compile := baseDirectory.value / "app"

scalaSource in Compile := baseDirectory.value / "app"

javaSource in Compile := baseDirectory.value / "app"

resourceDirectory in Compile := baseDirectory.value / "app"

// Al no ser proyecto play, hay que configurar a mano el encoding
javacOptions := List("-encoding", "utf-8") //, "-Xlint:unchecked")
