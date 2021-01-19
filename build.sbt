version := zero.git.version()

dependsOn(zio_nio)

libraryDependencies ++= Seq(
  "io.github.zero-deps" %% "ext" % "2.4.1.g7c28a4a"
, "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.11.3"
)
githubOwner := "zero-deps"
githubRepository := "ext"
scalaVersion := "2.13.4"

scalacOptions ++= Seq(
    "-deprecation"
  , "-encoding", "UTF-8"
  , "-explaintypes"
  , "-feature"
  , "-language:_"
  , "-unchecked"
  , "-Xfatal-warnings"
  , "-Xlint:adapted-args"
  , "-Xlint:constant"
  , "-Xlint:delayedinit-select"
  , "-Xlint:inaccessible"
  , "-Xlint:infer-any"
  , "-Xlint:missing-interpolator"
  , "-Xlint:nullary-unit"
  , "-Xlint:option-implicit"
  , "-Xlint:package-object-classes"
  , "-Xlint:poly-implicit-overload"
  , "-Xlint:private-shadow"
  , "-Xlint:stars-align"
  , "-Xlint:type-parameter-shadow"
  , "-Ypatmat-exhaust-depth", "off"
  , "-Ywarn-extra-implicit"
  , "-Ywarn-numeric-widen"
  , "-Ywarn-unused:implicits"
  , "-Ywarn-unused:imports"
  , "-Ywarn-unused:params"
  , "-Ywarn-value-discard"
  , "-Xmaxerrs", "1"
  , "-Xmaxwarns", "1"
  , "-Wconf:cat=deprecation&msg=Auto-application:silent"
)

lazy val zio_nio = project.in(file("deps/zio-nio/nio"))

turbo := true
useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges
