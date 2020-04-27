import _root_.play.core.PlayVersion
import _root_.play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val appName = "api-publisher"
lazy val appDependencies: Seq[ModuleID] = compile ++ test ++ tmpMacWorkaround

lazy val hmrcBootstrapPlay26Version = "1.4.0"
lazy val hmrcSimpleReactivemongoVersion = "7.22.0-play-26"
lazy val hmrcHttpMetricsVersion = "1.6.0-play-26"
lazy val hmrcReactiveMongoTestVersion = "4.16.0-play-26"
lazy val hmrcTestVersion = "3.9.0-play-26"
lazy val scalaJVersion = "2.4.1"
lazy val scalatestPlusPlayVersion = "3.1.2"
lazy val mockitoVersion = "1.10.19"
lazy val wireMockVersion = "2.21.0"


lazy val compile = Seq(
  "uk.gov.hmrc" %% "bootstrap-play-26" % hmrcBootstrapPlay26Version,
  "uk.gov.hmrc" %% "raml-tools" % "1.18.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % hmrcSimpleReactivemongoVersion,
  "org.json" % "json" % "20180130",
  "com.damnhandy" % "handy-uri-templates" % "2.1.6",
  "org.julienrf" %% "play-json-derived-codecs" % "6.0.0",
  "com.typesafe.play" %% "play-json" % "2.7.1",
  "org.typelevel" %% "cats-core" % "2.1.0"
)

lazy val scope: String = "test,it"

lazy val test = Seq(
  "uk.gov.hmrc" %% "bootstrap-play-26" % hmrcBootstrapPlay26Version % "test,it" classifier "tests",
  "uk.gov.hmrc" %% "reactivemongo-test" % hmrcReactiveMongoTestVersion % "test,it",
  "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
  "org.scalaj" %% "scalaj-http" % scalaJVersion % scope,
  "org.scalatest" %% "scalatest" % "3.0.5" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
  "org.mockito" % "mockito-core" % mockitoVersion % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "com.github.tomakehurst" % "wiremock" % wireMockVersion % scope
)

val jettyVersion = "9.2.24.v20180105"
// we need to override the akka version for now as newer versions are not compatible with reactivemongo
lazy val akkaVersion = "2.5.23"
lazy val akkaHttpVersion = "10.0.15"

val jettyOverrides: Seq[ModuleID] = Seq(
  "org.eclipse.jetty" % "jetty-server" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-security" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-continuation" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-xml" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-client" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-http" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-io" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-util" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-api" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-common" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-client" % jettyVersion % IntegrationTest,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
)

// Temporary Workaround for intermittent (but frequent) failures of Mongo integration tests when running on a Mac
// See Jira story GG-3666 for further information
def tmpMacWorkaround: Seq[ModuleID] = {
  if (sys.props.get("os.name").exists(_.toLowerCase.contains("mac"))) {
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.16.1-osx-x86-64" % "runtime,test,it")
  } else {
    Seq()
  }
}

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    name := appName,
    majorVersion := 0,
    scalaVersion := "2.12.11",
    targetJvm := "jvm-1.8",
    libraryDependencies ++= appDependencies,
    dependencyOverrides ++= jettyOverrides,
    parallelExecution in Test := false,
    fork in Test := false,
    retrieveManaged := true,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "app/resources"
  )
  .settings(
    testOptions in Test := Seq(Tests.Filter(_ => true)), // this removes duplicated lines in HTML reports
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    addTestReportOption(Test, "test-reports")
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := Seq((baseDirectory in IntegrationTest).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testOptions in IntegrationTest += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    parallelExecution in IntegrationTest := false)
  .settings(
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo,
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
  )
  .settings(scalacOptions ++= Seq("-Ypartial-unification"))

// Coverage configuration
coverageMinimum := 85
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
