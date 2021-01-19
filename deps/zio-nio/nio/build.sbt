lazy val zio_nio = project
  .in(file("."))
  .dependsOn(zio_nio_core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.3+130-a21e83b8-SNAPSHOT"
    , "dev.zio" %% "zio-test-sbt" % "1.0.3+130-a21e83b8-SNAPSHOT" % Test
    )
  , resolvers += Resolver.sonatypeRepo("snapshots")
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.0.0-M3"
  )

lazy val zio_nio_core = project.in(file("../nio-core"))