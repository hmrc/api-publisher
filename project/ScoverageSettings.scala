import scoverage.ScoverageKeys.*
  
object ScoverageSettings {
  private val excludesRegexes = Seq(
    "<empty>",
    ".*definition.*",
    "prod\\..*",
    "app\\..*",
    "testOnlyDoNotUseInAppConf\\..*",
    "uk\\.gov\\.hmrc\\.BuildInfo"
  )

  def apply() = Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    coverageExcludedPackages := excludesRegexes.mkString(";"),
    coverageMinimumStmtTotal := 85.00,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
