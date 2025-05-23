import sbt.*

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  val bootstrapVersion    = "9.11.0"
  val mongoVersion        = "2.6.0"
  val appDomainVersion    = "0.79.0"

  private val dependencies = Seq(
    "uk.gov.hmrc"         %% "bootstrap-backend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-play-30"              % mongoVersion,
    "org.json"             % "json"                            % "20230227",
    "com.damnhandy"        % "handy-uri-templates"             % "2.1.8",
    "org.julienrf"        %% "play-json-derived-codecs"        % "11.0.0",
    "org.typelevel"       %% "cats-core"                       % "2.10.0",
    "com.github.erosb"     % "everit-json-schema"              % "1.14.4",
    "uk.gov.hmrc"         %% "api-platform-application-domain" % appDomainVersion,
    "io.swagger.parser.v3" % "swagger-parser"                  % "2.1.14",
    "commons-io"           % "commons-io"                      % "2.14.0" // to fix CVE-2024-47554 until swagger-parser can be upgraded above 2.1.14
  )

  private val testDependencies = Seq(
    "uk.gov.hmrc"                   %% "bootstrap-test-play-30"                   % bootstrapVersion,
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-test-play-30"                  % mongoVersion,
    "com.softwaremill.sttp.client3" %% "core"                                     % "3.9.8",
    "org.mockito"                   %% "mockito-scala-scalatest"                  % "1.17.29",
    "uk.gov.hmrc"                   %% "api-platform-application-domain-fixtures" % appDomainVersion
  ).map(_ % "test")
}
