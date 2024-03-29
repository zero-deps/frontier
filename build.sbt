val scala = "3.2.0"

lazy val `ftier-root` = project
  .in(file("."))
  .aggregate(ftier, bot, demo, benchmark)

lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := scala
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.0.2"
    , "dev.zio" %% "zio-managed" % "2.0.2"
    , "dev.zio" %% "zio-test-sbt" % "2.0.2" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions ++= Seq(
      "-language:postfixOps"
    , "-language:strictEquality"
    , "-Yexplicit-nulls"
    , "-new-syntax"
    )
  )

lazy val bot = project
  .in(file("bot"))
  .settings(
    scalaVersion := scala
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % "0.3.0-RC11"
    )
  , scalacOptions ++= Seq(
      "-language:strictEquality"
    , "-Yexplicit-nulls"
    , "-new-syntax"
    )
  ).dependsOn(ftier)

lazy val demo = project
  .in(file("demo"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src"
  , scalaVersion := scala
  , scalacOptions ++= Seq(
      "-language:strictEquality"
    , "-Yexplicit-nulls"
    , "-new-syntax"
    )
  , run / fork := true
  ).dependsOn(ftier)

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % "0.23.11"
    , "org.http4s" %% "http4s-dsl" % "0.23.11"
    , "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.8.3" % "it"
    , "io.gatling"            % "gatling-test-framework"    % "3.8.3" % "it"
    )
  , scalaVersion := scala
  , run / fork := true
  ).dependsOn(ftier).enablePlugins(GatlingPlugin)

ThisBuild / turbo := true
Global / onChangedBuildSource := ReloadOnSourceChanges
