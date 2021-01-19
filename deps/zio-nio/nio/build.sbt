lazy val zio_nio = project
  .in(file("."))
  .dependsOn(zio_nio_core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.3"
    , "dev.zio" %% "zio-test-sbt" % "1.0.3" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "2.13.4"
  )

lazy val zio_nio_core = project.in(file("../nio-core"))