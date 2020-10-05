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

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / cancelable := true
Global / fork := true

lazy val saw = project.in(file(".")).settings(
  libraryDependencies ++= Seq(
    "dev.zio"                                %% "zio-nio"               % "1.0.0-RC6" // "1.0.0-RC9"
  , "dev.zio"                                %% "zio-akka-cluster"      % "0.1.13" /* "0.2.0" */ excludeAll(ExclusionRule(organization = "dev.zio"))
  // , "com.github.plokhotnyuk.jsoniter-scala"  %% "jsoniter-scala-core"   % "2.6.0"
  // , "com.github.plokhotnyuk.jsoniter-scala"  %% "jsoniter-scala-macros" % "2.6.0" % "compile-internal"
  // // , "org.scalacheck"                         %% "scalacheck"            % "1.14.1" % Test
  // , "org.scalatest"                          %% "scalatest"             % "3.2.2" % Test
  // , "io.github.zero-deps"                    %% "kvs-core"              % "5.3"
  // , "org.glassfish" % "jakarta.json" % "2.0.0-RC3"
  // , "org.rocksdb"                            % "rocksdbjni"            % "6.4.6"
  // , "ch.ethz.globis.phtree"                  % "phtree"                % "2.5.0"
  // , "redis.clients"                          % "jedis"                  % "3.3.0"
  )
)

ThisBuild / resolvers += Resolver.bintrayRepo("zero-deps", "maven")

ThisBuild / publishArtifact in (Compile, packageDoc) := false