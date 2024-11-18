import sbt.*

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  val bootstrapVersion    = "9.1.0"
  val mongoVersion        = "2.1.0"
  val commonDomainVersion = "0.15.0"
  val appDomainVersion    = "0.62.0"

  private val dependencies = Seq(
    "uk.gov.hmrc"         %% "bootstrap-backend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc"         %% "raml-tools"                      % "1.24.0",
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-play-30"              % mongoVersion,
    "org.json"             % "json"                            % "20230227",
    "com.damnhandy"        % "handy-uri-templates"             % "2.1.8",
    "org.julienrf"        %% "play-json-derived-codecs"        % "11.0.0",
    "org.typelevel"       %% "cats-core"                       % "2.10.0",
    "uk.gov.hmrc"         %% "api-platform-common-domain"      % commonDomainVersion,
    "com.github.erosb"     % "everit-json-schema"              % "1.14.4",
    "uk.gov.hmrc"         %% "api-platform-application-domain" % appDomainVersion,
    "io.swagger.parser.v3" % "swagger-parser"                  % "2.1.14"
  )

  private val testDependencies = Seq(
    "uk.gov.hmrc"                   %% "bootstrap-test-play-30"                   % bootstrapVersion,
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-test-play-30"                  % mongoVersion,
    "com.softwaremill.sttp.client3" %% "core"                                     % "3.9.8",
    "org.mockito"                   %% "mockito-scala-scalatest"                  % "1.17.29",
    "uk.gov.hmrc"                   %% "api-platform-test-common-domain"          % commonDomainVersion,
    "uk.gov.hmrc"                   %% "api-platform-application-domain-fixtures" % appDomainVersion
  ).map(_ % "test")
}
