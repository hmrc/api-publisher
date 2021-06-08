import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-play-26"            % "1.4.0",
    "uk.gov.hmrc"               %% "raml-tools"                   % "1.18.0",
    "uk.gov.hmrc"               %% "simple-reactivemongo"         % "7.30.0-play-26",
    "org.json"                  % "json"                          % "20180130",
    "com.damnhandy"             % "handy-uri-templates"           % "2.1.6",
    "org.julienrf"              %% "play-json-derived-codecs"     % "6.0.0",
    "com.typesafe.play"         %% "play-json"                    % "2.7.1",
    "org.typelevel"             %% "cats-core"                    % "2.1.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-play-26"            % "1.4.0"                         % "test,it" classifier "tests",
    "uk.gov.hmrc"               %% "reactivemongo-test"           % "4.21.0-play-26"                % "test,it",
    "uk.gov.hmrc"               %% "hmrctest"                     % "3.9.0-play-26"                 % "test,it",
    "org.scalaj"                %% "scalaj-http"                  % "2.4.1"                         % "test,it",
    "org.scalatestplus.play"    %% "scalatestplus-play"           % "3.1.3"                         % "test,it",
    "org.mockito"               % "mockito-core"                  % "1.10.19"                       % "test,it",
    "com.typesafe.play"         %% "play-test"                    % PlayVersion.current             % "test,it",
    "com.github.tomakehurst"    % "wiremock"                      % "2.21.0"                        % "test,it"
  )
}
