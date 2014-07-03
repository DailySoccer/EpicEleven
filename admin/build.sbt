name := "admin"

version := "1.0-SNAPSHOT"

play.Project.playJavaSettings

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.json" % "json" % "20140107"
)

// Un @import comun a todas las view templates, para que quede mas compacto
templatesImport += "admin.routes.AdminController"