// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects.
// The Play console and all of its development features like live reloading are implemented via an sbt plugin.
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.3")
