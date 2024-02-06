import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  lazy val bootstrapVersion = "8.4.0"
  lazy val mongoVersion = "1.7.0"
  val commonDomainVersion = "0.11.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-backend-play-30"    % bootstrapVersion,
    "uk.gov.hmrc"               %% "raml-tools"                   % "1.24.0",
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-30"           % mongoVersion,
    "org.json"                  %  "json"                         % "20230227",
    "com.damnhandy"             %  "handy-uri-templates"          % "2.1.8",
    "org.julienrf"              %% "play-json-derived-codecs"     % "10.1.0",
    "org.typelevel"             %% "cats-core"                    % "2.10.0",
    "uk.gov.hmrc"               %% "api-platform-common-domain"   % commonDomainVersion,
    "com.github.erosb"          %  "everit-json-schema"           % "1.14.4",
    "io.swagger.parser.v3"      %  "swagger-parser"               % "2.1.14"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"           % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"          % mongoVersion,
    "org.scalaj"              %% "scalaj-http"                      % "2.4.2",
    "org.mockito"             %% "mockito-scala-scalatest"          % "1.17.29"
  ).map(_ % "test,it")
}
