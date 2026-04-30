import sbt.*

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  val bootstrapVersion    = "10.7.0"
  val mongoVersion        = "2.12.0"
  val commonDomainVersion = "1.0.0"
  val mockitoScalaVersion = "2.0.0"

  private val dependencies = Seq(
    "uk.gov.hmrc"         %% "bootstrap-backend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-play-30"              % mongoVersion,
    "org.json"             % "json"                            % "20230227",
    "com.damnhandy"        % "handy-uri-templates"             % "2.1.8",
    "org.julienrf"        %% "play-json-derived-codecs"        % "11.0.0",
    "org.typelevel"       %% "cats-core"                       % "2.13.0",
    "com.github.erosb"     % "everit-json-schema"              % "1.14.4",
    "uk.gov.hmrc"         %% "api-platform-common-domain"      % commonDomainVersion,
    "io.swagger.parser.v3" % "swagger-parser"                  % "2.1.14",
    "commons-io"           % "commons-io"                      % "2.14.0" // to fix CVE-2024-47554 until swagger-parser can be upgraded above 2.1.14
  )

  private val testDependencies = Seq(
    "uk.gov.hmrc"                   %% "bootstrap-test-play-30"                   % bootstrapVersion,
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-test-play-30"                  % mongoVersion,
    "com.softwaremill.sttp.client3" %% "core"                                     % "3.11.0",
    "org.mockito"                   %% "mockito-scala-scalatest"                  % mockitoScalaVersion,
    "uk.gov.hmrc"                   %% "api-platform-common-domain-fixtures"      % commonDomainVersion
  ).map(_ % "test")

   private val circeVersion = "0.14.15"

  val scriptDependencies = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % circeVersion)
}
