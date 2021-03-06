lazy val ftier = project
  .in(file("ftier"))
  .settings(
    scalaVersion := "3.0.2-RC1"
  , crossScalaVersions := "3.0.2-RC1" :: "2.13.6" :: Nil
  , libraryDependencies ++= Seq(
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.0-rc1"
    , "dev.zio" %% "zio-test-sbt" % "1.0.9" % Test
    )
  , resolvers += Resolver.sonatypeRepo("snapshots")
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalacOptions += "-language:postfixOps"
  , scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Nil
        case _ => Seq(
          "-source:future"
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
      "dev.zio" %% "zio-test-sbt" % "1.0.9" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.0.2-RC1"
  , crossScalaVersions := "3.0.2-RC1" :: "2.13.6" :: Nil
  , scalacOptions += "-nowarn"
  )
  .dependsOn(zio_nio_core)

lazy val zio_nio_core = project
  .in(file("deps/zio-nio/nio-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams"  % "1.0.9"
    , "dev.zio" %% "zio-test-sbt" % "1.0.9" % Test
    )
  , testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  , scalaVersion := "3.0.2-RC1"
  , crossScalaVersions := "3.0.2-RC1" :: "2.13.6" :: Nil
  , scalacOptions += "-nowarn"
  )

ThisBuild / turbo := true
ThisBuild / useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges
