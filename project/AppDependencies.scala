import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  lazy val bootstrapVersion = "7.23.0"
  lazy val jacksonVersion = "2.12.6"
  lazy val mongoVersion = "0.74.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc"               %% "raml-tools"                   % "1.23.0",
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-28"           % mongoVersion,
    "org.json"                  %  "json"                         % "20210307",
    "com.damnhandy"             %  "handy-uri-templates"          % "2.1.8",
    "org.julienrf"              %% "play-json-derived-codecs"     % "10.0.2",
    "org.typelevel"             %% "cats-core"                    % "2.6.1",
    "io.swagger.parser.v3"      %  "swagger-parser"               % "2.1.9"
      excludeAll(
        ExclusionRule("com.fasterxml.jackson.core", "jackson-databind"),
        ExclusionRule("com.fasterxml.jackson.core", "jackson-core"),
        ExclusionRule("com.fasterxml.jackson.core", "jackson-annotations"),
        ExclusionRule("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml"),
        ExclusionRule("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310")
      ),
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"           % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"          % mongoVersion,
    "com.vladsch.flexmark"     % "flexmark-all"                     % "0.62.2",
    "org.scalaj"              %% "scalaj-http"                      % "2.4.2",
    "com.typesafe.play"       %% "play-test"                        % PlayVersion.current,
    "com.github.tomakehurst"  %  "wiremock-jre8-standalone"         % "2.31.0",
    "org.mockito"             %% "mockito-scala-scalatest"          % "1.17.29",
    "org.scalatest"           %% "scalatest"                        % "3.2.17"
  ).map(_ % "test,it")
}
