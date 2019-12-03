import _root_.play.core.PlayVersion
import _root_.play.sbt.PlayScala
import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val appName = "api-publisher"
lazy val appDependencies: Seq[ModuleID] = compile ++ test ++ tmpMacWorkaround

lazy val compile = Seq(
  "uk.gov.hmrc" %% "bootstrap-play-25" % "5.1.0",
  "uk.gov.hmrc" %% "raml-tools" % "1.11.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-25",
  "org.json" % "json" % "20180130",
  "com.damnhandy" % "handy-uri-templates" % "2.1.6"
)

lazy val scope: String = "test,it"

lazy val test = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25" % scope,
  "org.scalaj" %% "scalaj-http" % "2.4.0" % scope,
  "org.scalatest" %% "scalatest" % "3.0.5" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
  "org.mockito" % "mockito-core" % "2.27.0" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "com.github.tomakehurst" % "wiremock" % "2.11.0" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-25" % scope
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
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    testOptions in IntegrationTest += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    parallelExecution in IntegrationTest := false)
  .settings(
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo
  )
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
