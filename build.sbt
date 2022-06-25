lazy val `ftier-root` = project
  .in(file("."))
  .aggregate(ftier)
  // .aggregate(ftier, tg, demo, benchmark)

lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := "3.2.0-RC1"
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-nio" % "2.0.0"
    , "dev.zio" %% "zio-test-sbt" % "2.0.0" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions ++= Seq(
      "-language:postfixOps"
    , "-language:strictEquality"
    , "-Yexplicit-nulls"
    )
  )

lazy val tg = project
  .in(file("tg"))
  .settings(
    scalaVersion := "3.2.0-RC1"
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % "0.3.0-RC8"
    )
  , scalacOptions ++= Seq(
      "-language:strictEquality"
    , "-Yexplicit-nulls"
    )
  ).dependsOn(ftier)

lazy val demo = project
  .in(file("demo"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src"
  , scalaVersion := "3.2.0-RC1"
  , scalacOptions ++= Seq(
      "-language:strictEquality"
    , "-Yexplicit-nulls"
    )
  , run / fork := true
  ).dependsOn(ftier)

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % "0.23.11"
    , "org.http4s" %% "http4s-dsl" % "0.23.11"
    , "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.6" % "it"
    , "io.gatling"            % "gatling-test-framework"    % "3.7.6" % "it"
    )
  , scalaVersion := "3.2.0-RC1"
  , run / fork := true
  ).dependsOn(ftier).enablePlugins(GatlingPlugin)

ThisBuild / turbo := true
Global / onChangedBuildSource := ReloadOnSourceChanges
