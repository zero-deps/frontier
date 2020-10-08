ThisBuild / organization := "io.github.zero-deps"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / scalacOptions ++= Seq(
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
  , "-Ywarn-dead-code"
  , "-Ywarn-extra-implicit"
  , "-Ywarn-numeric-widen"
  , "-Ywarn-unused:implicits"
  // , "-Ywarn-unused:imports"
  // , "-Ywarn-unused:params"
  , "-Ywarn-value-discard"
  , "-Xmaxerrs", "1"
  , "-Xmaxwarns", "3"
  , "-Wconf:cat=deprecation&msg=Auto-application:silent"
)

ThisBuild / turbo := true
ThisBuild / useCoursier := true
Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val frontier = project.in(file(".")).settings(
  libraryDependencies ++= Seq(
    "dev.zio"                                %% "zio-nio"               % "1.0.0-RC6" // "1.0.0-RC9"
  , "dev.zio"                                %% "zio-akka-cluster"      % "0.1.13" /* "0.2.0" */ excludeAll(ExclusionRule(organization = "dev.zio"))
  , "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.11.3"
  )
)

ThisBuild / resolvers += Resolver.bintrayRepo("zero-deps", "maven")

ThisBuild / publishArtifact in (Compile, packageDoc) := false
