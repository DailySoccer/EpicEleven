// Comment to get more information during initialization
logLevel := Level.Warn

resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
                 ,Resolver.url("heroku-sbt-plugin-releases", url("http://dl.bintray.com/heroku/sbt-plugins/"))(Resolver.ivyStylePatterns))

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.5")

addSbtPlugin("com.heroku" % "sbt-heroku" % "0.3.4")