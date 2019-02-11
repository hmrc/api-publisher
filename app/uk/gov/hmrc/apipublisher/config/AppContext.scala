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

package uk.gov.hmrc.apipublisher.config

import javax.inject.Inject
import play.api.Mode.Mode
import play.api.{Configuration, Play}
import uk.gov.hmrc.play.config.ServicesConfig

class AppContext @Inject()(configuration: Configuration) extends ServicesConfig {

  lazy val appName = configuration.getString("appName").getOrElse(throw new RuntimeException("appName is not configured"))
  lazy val appUrl = configuration.getString("appUrl").getOrElse(throw new RuntimeException("appUrl is not configured"))
  lazy val publisherUrl = s"$appUrl/publish"
  lazy val preventAutoDeploy: Boolean = configuration.getBoolean(s"$env.features.preventAutoDeploy").getOrElse(false)
  lazy val ramlLoaderRewrites = buildRamlLoaderRewrites(configuration)

  private def buildRamlLoaderRewrites(config: Configuration): Map[String, String] = {

    val from = config.getString(s"$env.ramlLoaderUrlRewrite.from")
      .getOrElse(throw new RuntimeException("ramlLoaderRewrite.from is not configured"))

    val to = config.getString(s"$env.ramlLoaderUrlRewrite.to")
      .getOrElse(throw new RuntimeException("ramlLoaderRewrite.to is not configured"))

    Map(from -> to)
  }

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration
}
