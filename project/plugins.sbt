scalaVersion := "2.12.7"

// bootstrap
lazy val izumiDepsPlugin = RootProject(file("../sbt/sbt-izumi-deps"))
lazy val izumiPlugin = RootProject(file("../sbt/sbt-izumi"))
lazy val root = project.in(file(".")).dependsOn(izumiDepsPlugin, izumiPlugin)

// https://github.com/sbt/sbt-assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.7")

// https://www.scala-sbt.org/sbt-site/
//addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.2")

// https://jonas.github.io/paradox-material-theme/
//addSbtPlugin("io.github.jonas" % "sbt-paradox-material-theme" % "0.5.1")

// https://github.com/sbt/sbt-unidoc
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")

// https://github.com/sbt/sbt-ghpages
//addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")

// https://github.com/47deg/sbt-microsites
addSbtPlugin("com.47deg"  % "sbt-microsites" % "0.7.23")

//
libraryDependencies += {
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
}


