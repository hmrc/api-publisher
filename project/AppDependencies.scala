import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  lazy val bootstrapVersion = "5.16.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28"    % bootstrapVersion,
    "uk.gov.hmrc"               %% "raml-tools"                   % "1.20.0",
    "uk.gov.hmrc"               %% "simple-reactivemongo"         % "8.0.0-play-28",
    "org.json"                  %  "json"                         % "20180130",
    "com.damnhandy"             %  "handy-uri-templates"          % "2.1.6",
    "org.julienrf"              %% "play-json-derived-codecs"     % "9.0.0",
    "org.typelevel"             %% "cats-core"                    % "2.1.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-28"       % bootstrapVersion         % "test,it",
    "uk.gov.hmrc"               %% "reactivemongo-test"           % "5.0.0-play-28"          % "test,it",
    "org.scalaj"                %% "scalaj-http"                  % "2.4.1"                   % "test,it",
    "org.mockito"               %% "mockito-scala-scalatest"      % "1.7.1"                   % "test,it",
    "com.typesafe.play"         %% "play-test"                    % PlayVersion.current       % "test,it",
    "com.typesafe.play"         %% "play-akka-http-server"        % "2.8.7"                   % "test,it",
    "com.github.tomakehurst"    %  "wiremock-jre8-standalone"     % "2.27.1"                  % "test,it"
  )
}
