import scala.language.implicitConversions

val scalaV = "3.8.4"

lazy val `ftier-root` = project
  .in(file("."))
  .aggregate(ftier, demo)

lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := scalaV
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.1.26"
    , "dev.zio" %% "zio-test-sbt" % "2.1.26" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions ++= Seq(
      "-language:postfixOps"
    , "-language:strictEquality"
    , "-Wunused:imports"
    , "-Xfatal-warnings"
    , "-Yexplicit-nulls"
    )
  )

lazy val demo = project
  .in(file("demo"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src"
  , scalaVersion := scalaV
  , run / fork := true
  ).dependsOn(ftier)

Global / onChangedBuildSource := ReloadOnSourceChanges
