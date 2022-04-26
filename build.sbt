lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := "3.1.3-RC2"
  , libraryDependencies ++= Seq(
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.1"
    , "dev.zio" %% "zio-test-sbt" % "1.0.14" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions += "-language:postfixOps"
  , scalacOptions ++= Seq(
      "-language:strictEquality"
    , "-Yexplicit-nulls"
    , "release", "18"
    )
  )
  .dependsOn(zio_nio)
  .aggregate(zio_nio, zio_nio_core)

lazy val zio_nio = project
  .in(file("deps/zio-nio/nio"))
  .dependsOn(zio_nio_core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test-sbt" % "1.0.14" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.1.3-RC2"
  , scalacOptions ++= Seq(
      "-nowarn"
    , "release", "18"
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
  , scalaVersion := "3.1.3-RC2"
  , scalacOptions ++= Seq(
      "-nowarn"
    , "release", "18"
    )
  )

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % "0.23.11"
    , "org.http4s" %% "http4s-dsl" % "0.23.11"
    , "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.6" % "it"
    , "io.gatling"            % "gatling-test-framework"    % "3.7.6" % "it"
    )
  , scalaVersion := "3.1.3-RC2"
  , scalacOptions ++= Seq(
      "release", "18"
    )
  , run / fork := true
  ).dependsOn(ftier).enablePlugins(GatlingPlugin)

ThisBuild / turbo := true
ThisBuild / useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges
