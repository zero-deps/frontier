lazy val zio_nio_core = project
  .in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.4-2"
    , "dev.zio" %% "zio-test-sbt" % "1.0.4-2" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.0.0-M3"
  )