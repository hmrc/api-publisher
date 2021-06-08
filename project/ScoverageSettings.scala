import scoverage.ScoverageKeys._
  
object ScoverageSettings {
  private val excludesRegexes = Seq(
    "<empty>",
    "com\\.kenshoo\\.play\\.metrics\\..*",
    ".*definition.*",
    "prod\\..*",
    "app\\..*",
    "testOnlyDoNotUseInAppConf\\..*",
    "uk\\.gov\\.hmrc\\.BuildInfo"
  )

  def apply() = Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    coverageExcludedPackages := excludesRegexes.mkString(";"),
    coverageMinimum := 90.00,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
