import com.typesafe.sbt.packager.SettingsHelper._

name := "sms-bridge"

lazy val root = (project in file(".")).enablePlugins(SbtNativePackager, JavaAppPackaging, UniversalDeployPlugin)

scalaVersion := "2.11.7"

organization := "com.pjanof"

resolvers ++= Seq(
  "Maven Releases" at "http://repo.typesafe.com/typesafe/maven-releases"
  , "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases" )

libraryDependencies ++= {
  val akkaV       = "2.3.12"
  val akkaStreamV = "1.0"
  val scalaTestV  = "2.2.4"

  Seq(
      "ch.qos.logback"              %   "logback-classic"                   % "1.1.3"
    , "com.typesafe.scala-logging"  %%  "scala-logging"                     % "3.1.0"
    , "com.typesafe.akka"           %%  "akka-actor"                        % akkaV
    , "com.typesafe.akka"           %%  "akka-slf4j"                        % akkaV
    , "com.typesafe.akka"           %%  "akka-testkit"                      % akkaV % "test"
    , "com.typesafe.akka"           %%  "akka-stream-experimental"          % akkaStreamV
    , "com.typesafe.akka"           %%  "akka-http-core-experimental"       % akkaStreamV
    , "com.typesafe.akka"           %%  "akka-http-experimental"            % akkaStreamV
    , "com.typesafe.akka"           %%  "akka-http-spray-json-experimental" % akkaStreamV
    , "com.typesafe.akka"           %%  "akka-http-testkit-experimental"    % akkaStreamV
    , "com.gettyimages"             %%  "spray-swagger"                     % "0.5.1"
    , "org.scalaz"                  %%  "scalaz-core"                       % "7.1.0"
    , "javax.sip"                   %   "jain-sip-ri"                       % "1.2.258"
    , "org.mobicents.javax.sip"     %   "mobicents-jain-sip-ext"            % "1.3.11"
    , "org.scalatest"               %%  "scalatest"                         % "2.2.4" % "test"
    , "io.gatling.highcharts"       %   "gatling-charts-highcharts"         % "2.1.7" ) }

// continuous build
Revolver.settings

// run options
javaOptions in run ++= Seq(
  "-Dconfig.file=src/main/resources/application.conf",
  "-Dlogback.configurationFile=src/main/resources/logback.xml"
)

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

// test options
javaOptions in Test += "-Dconfig.file=src/test/resources/application.conf"

scalacOptions in Test ++= Seq("-Yrangepos")

fork := true

// deploy
deploymentSettings

publishMavenStyle := true

// sbt-native-packager - universal:publish
makeDeploymentSettings(Universal, packageZipTarball in Universal, "tgz")

// sbt-release - publish
val packageTgz = taskKey[File]("package-zip-tarball")

packageTgz := (baseDirectory in Compile).value / "target" / "universal" / (name.value + "-" + version.value + ".tgz")

artifact in (Universal, packageTgz) ~= { (art:Artifact) => art.copy(`type` = "tgz", extension = "tgz") }

addArtifact(artifact in (Universal, packageTgz), packageTgz in Universal)

publish <<= (publish) dependsOn (packageZipTarball in Universal)
