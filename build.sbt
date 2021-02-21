version := zero.git.version()
scalaVersion := "3.0.0-M3"

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.13" % "2.12.1"
, "dev.zio" %% "zio-test-sbt" % "1.0.4-2" % Test
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

scalacOptions ++= Seq(
  "-language:postfixOps"
// , "-Yexplicit-nulls"
, "-language:strictEquality"
)

dependsOn(zio_nio, ext)
lazy val zio_nio = project.in(file("deps/zio-nio/nio"))
lazy val ext = project.in(file("deps/ext"))

turbo := true
useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers += Resolver.JCenterRepository
