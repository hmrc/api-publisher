/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apipublisher.wiring

import javax.inject.Inject
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

class AppContext @Inject()(val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig {

  lazy val appName = runModeConfiguration.getString("appName").getOrElse(throw new RuntimeException("appName is not configured"))
  lazy val appUrl = runModeConfiguration.getString("appUrl").getOrElse(throw new RuntimeException("appUrl is not configured"))
  lazy val publisherUrl = s"$appUrl/publish"
  lazy val preventAutoDeploy: Boolean = runModeConfiguration.getBoolean(s"$env.features.preventAutoDeploy").getOrElse(false)
  lazy val ramlLoaderRewrites = buildRamlLoaderRewrites(runModeConfiguration)
  lazy val publishToken = runModeConfiguration.getString("publishToken").getOrElse(throw new RuntimeException("publishToken is not configured"))
  lazy val publishingKey = runModeConfiguration.getString("publishingKey").getOrElse(throw new RuntimeException("publishingKey is not configured"))
  lazy val registrationEnabled = getConfBool("service-locator.enabled", defBool = false)

  private def buildRamlLoaderRewrites(runModeConfiguration: Configuration): Map[String, String] = {

    val from = runModeConfiguration.getString(s"$env.ramlLoaderUrlRewrite.from")
      .getOrElse(throw new RuntimeException("ramlLoaderRewrite.from is not configured"))

    val to = runModeConfiguration.getString(s"$env.ramlLoaderUrlRewrite.to")
      .getOrElse(throw new RuntimeException("ramlLoaderRewrite.to is not configured"))

    Map(from -> to)
  }

  override protected def mode: Mode = environment.mode
}
