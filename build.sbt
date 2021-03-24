version := zero.git.version()
scalaVersion := "3.0.0-RC1"
crossScalaVersions := "3.0.0-RC1" :: "2.13.5" :: Nil

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.1" cross CrossVersion.for3Use2_13
, "dev.zio" %% "zio-test-sbt" % "1.0.5" % Test
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

scalacOptions ++= Seq(
  "-language:postfixOps"
, "-language:strictEquality"
// , "-Yexplicit-nulls"
, "-source", "future-migration"
, "-deprecation"
, "-rewrite"
, "release", "11"
)

dependsOn(zio_nio, ext)
lazy val zio_nio = project.in(file("deps/zio-nio/nio"))
lazy val ext = project.in(file("deps/ext"))

turbo := true
useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers += Resolver.JCenterRepository
