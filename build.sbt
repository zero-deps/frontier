lazy val `ftier-root` = project
  .in(file("."))
  .aggregate(ftier, /*bot, */demo, benchmark)

lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := "3.2.0-RC1"
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test-sbt" % "1.0.14" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions ++= Seq(
      "-language:postfixOps"
    , "-language:strictEquality"
    , "-Yexplicit-nulls"
    )
  )
  .dependsOn(zio_nio)
  .aggregate(zio_nio, zio_nio_core)

lazy val bot = project
  .in(file("bot"))
  .settings(
    scalaVersion := "3.2.0-RC1"
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % "0.2.0-M4"
    )
  , scalacOptions ++= Seq(
      "-language:strictEquality"
    , "-Yexplicit-nulls"
    )
  ).dependsOn(ftier)

lazy val zio_nio = project
  .in(file("deps/zio-nio/nio"))
  .dependsOn(zio_nio_core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test-sbt" % "1.0.14" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.2.0-RC1"
  , scalacOptions ++= Seq(
      "-nowarn"
    )
  )
  .dependsOn(zio_nio_core)

lazy val zio_nio_core = project
  .in(file("deps/zio-nio/nio-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.14"
    , "dev.zio" %% "zio-test-sbt" % "1.0.14" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.2.0-RC1"
  , scalacOptions += "-nowarn"
  )

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
