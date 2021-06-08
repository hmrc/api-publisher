import play.core.PlayVersion
import play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import bloop.integrations.sbt.BloopDefaults

lazy val appName = "api-publisher"

// Temporary Workaround for intermittent (but frequent) failures of Mongo integration tests when running on a Mac
// See Jira story GG-3666 for further information
def tmpMacWorkaround: Seq[ModuleID] = {
  if (sys.props.get("os.name").exists(_.toLowerCase.contains("mac"))) {
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.16.1-osx-x86-64" % "runtime,test,it")
  } else {
    Seq()
  }
}

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(ScoverageSettings())
  .settings(
    name := appName,
    scalaVersion := "2.12.12",
    targetJvm := "jvm-1.8",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "app/resources"
  )
  .settings(inConfig(Test)(BloopDefaults.configSettings))
  .settings(
    // testOptions in Test := Seq(Tests.Filter(_ => true)), // this removes duplicated lines in HTML reports
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    Test / fork := false,
    Test / parallelExecution := false,
    Test / unmanagedSourceDirectories += baseDirectory.value / "test",
    Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    addTestReportOption(Test, "test-reports")
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(inConfig(IntegrationTest)(BloopDefaults.configSettings))
  .settings(
    IntegrationTest / fork := false,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory.value / "it",
    IntegrationTest / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    IntegrationTest / parallelExecution:= false
  )
  .settings(
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
  )
  .settings(scalacOptions ++= Seq("-Ypartial-unification"))

