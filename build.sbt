name := "cupboard"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "io.spray" %%  "spray-json" % "1.3.2",
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "com.google.cloud" % "google-cloud-datastore" % "1.98.0",
  "org.typelevel" %% "cats" % "0.6.0",
  "org.typelevel" %% "macro-compat" % "1.1.1"
)

scalacOptions ++= Seq(
  "-language:reflectiveCalls"   // necessary until we implement "vampire methods" to eliminate this warning
)

parallelExecution in Test := false

fork in Test := true

enablePlugins(CommonSettingsPlugin)
enablePlugins(CoverallsWrapper)

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

bintrayOrganization in ThisBuild := Some("meetup")

licenses in ThisBuild += ("MIT", url("http://opensource.org/licenses/MIT"))
