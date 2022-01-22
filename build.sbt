lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := "3.1.1"
  , libraryDependencies ++= Seq(
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.1"
    , "dev.zio" %% "zio-test-sbt" % "1.0.10" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions += "-language:postfixOps"
  , scalacOptions ++= Seq(
      "-source:future"
    , "-language:strictEquality"
    , "-Yexplicit-nulls"
    , "release", "11"
    )
  )
  .dependsOn(zio_nio)
  .aggregate(zio_nio, zio_nio_core)

lazy val zio_nio = project
  .in(file("deps/zio-nio/nio"))
  .dependsOn(zio_nio_core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test-sbt" % "1.0.10" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.1.1"
  , scalacOptions += "-nowarn"
  )
  .dependsOn(zio_nio_core)

lazy val zio_nio_core = project
  .in(file("deps/zio-nio/nio-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.10"
    , "dev.zio" %% "zio-test-sbt" % "1.0.10" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.1.1"
  , scalacOptions += "-nowarn"
  )

ThisBuild / turbo := true
ThisBuild / useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges
