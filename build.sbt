import _root_.play.core.PlayVersion
import _root_.play.routes.compiler.StaticRoutesGenerator
import _root_.play.sbt.PlayImport._
import _root_.play.sbt.PlayScala
import _root_.play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val appName = "api-publisher"
lazy val appDependencies: Seq[ModuleID] = compile ++ test

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-25" % "4.9.0",
  "uk.gov.hmrc" %% "raml-tools" % "1.11.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.12.0-play-25"
)

lazy val scope: String = "test,it"

lazy val test = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.4.0-play-25" % scope,
  "org.scalaj" %% "scalaj-http" % "2.4.0" % scope,
  "org.scalatest" %% "scalatest" % "3.0.4" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
  "org.mockito" % "mockito-core" % "2.11.0" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "com.github.tomakehurst" % "wiremock" % "2.11.0" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.7.0-play-25" % scope
)

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    name := appName,
    majorVersion := 0,
    scalaVersion := "2.11.11",
    targetJvm := "jvm-1.8",
    libraryDependencies ++= appDependencies,
    parallelExecution in Test := false,
    fork in Test := false,
    retrieveManaged := true
  )
   .settings(
      testOptions in Test := Seq(Tests.Filter(_ => true)),// this removes duplicated lines in HTML reports
      addTestReportOption(Test, "test-reports")
    )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := Seq((baseDirectory in IntegrationTest).value / "it" ),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  .settings(
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo)
  .settings(ivyScala := ivyScala.value map {
    _.copy(overrideScalaVersion = true)
  })

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
  tests map {
    test => Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
  }

// Coverage configuration
coverageMinimum := 85
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
