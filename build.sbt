val scala = "3.3.3"

lazy val `ftier-root` = project
  .in(file("."))
  .aggregate(ftier, demo, benchmark, it, ws)

lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := scala
  , libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.1.9"
    , "dev.zio" %% "zio-test-sbt" % "2.1.9" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions ++= Seq(
      "-language:postfixOps"
    , "-language:strictEquality"
    , "-Wunused:imports"
    , "-Xfatal-warnings"
    , "-Yexplicit-nulls"
    , "-release", "21"
    )
  )

lazy val demo = project
  .in(file("demo"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src"
  , scalaVersion := scala
  , scalacOptions ++= Seq(
      "-Wunused:imports"
    , "-Xfatal-warnings"
    )
  , run / fork := true
  ).dependsOn(ftier)

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src"
  , resolvers += "Akka".at("https://repo.akka.io/maven")
  , libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.6.3"
    , "com.typesafe.akka" %% "akka-stream" % "2.9.3"
    , "dev.zio" %% "zio-http" % "3.0.0"
    )
  , scalaVersion := scala
  , scalacOptions ++= Seq(
      "-Wunused:imports"
    , "-Xfatal-warnings"
    , "-release", "21"
  )
  , run / fork := true
  , run / javaOptions += "-Xmx2g"
  ).dependsOn(ftier)

lazy val it = project
  .in(file("it"))
  .settings(
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.12.0" % "it"
    , "io.gatling" % "gatling-test-framework" % "3.12.0" % "it"
    )
  , scalaVersion := "2.13.14"
  , scalacOptions ++= Seq(
      "-Xsource:3"
    , "-release", "11"
    )
  , run / fork := true
  , run / javaOptions += "-Xmx2g"
  ).enablePlugins(GatlingPlugin)

lazy val ws = project
  .in(file("ws"))
  .settings(
    scalaVersion := "3.5.0"
  , scalacOptions ++= Seq(
      "-release", "22"
    )
  , run / fork := true
  , run / javaOptions += "-Xmx2g"
  )

ThisBuild / turbo := true
Global / onChangedBuildSource := ReloadOnSourceChanges
