lazy val ftier = project
  .in(file("."))
  .settings(
    scalaVersion := "3.0.0-RC1"
  , crossScalaVersions := "3.0.0-RC1" :: "2.13.5" :: Nil
  , libraryDependencies ++= Seq(
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.13" % "2.12.1"
    , "dev.zio" %% "zio-test-sbt" % "1.0.5" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions += "-language:postfixOps"
  , scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Nil
        case _ => Seq(
          "-source", "future-migration", "-deprecation"
        , "-language:strictEquality"
        , "-Yexplicit-nulls"
        , "release", "11"
        )
      }
    }
  )
  .dependsOn(zio_nio)
  .aggregate(zio_nio, zio_nio_core)

lazy val zio_nio = project
  .in(file("deps/zio-nio/nio"))
  .dependsOn(zio_nio_core)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test-sbt" % "1.0.5" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.0.0-RC1"
  , crossScalaVersions := "3.0.0-RC1" :: "2.13.5" :: Nil
  , scalacOptions += "-nowarn"
  )
  .dependsOn(zio_nio_core)

lazy val zio_nio_core = project
  .in(file("deps/zio-nio/nio-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.5"
    , "dev.zio" %% "zio-test-sbt" % "1.0.5" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.0.0-RC1"
  , crossScalaVersions := "3.0.0-RC1" :: "2.13.5" :: Nil
  , scalacOptions += "-nowarn"
  )

turbo := true
useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges
