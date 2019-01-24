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

package uk.gov.hmrc.apipublisher.services

import javax.inject.{Inject, Singleton}

import play.api.{Configuration, Play}
import play.api.Mode.Mode
import uk.gov.hmrc.apipublisher.config.AppContext
import uk.gov.hmrc.apipublisher.connectors.ServiceLocatorConnector
import uk.gov.hmrc.apipublisher.models.Subscription
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future

@Singleton
class RegistrationService @Inject()(val serviceLocatorConnector : ServiceLocatorConnector,
                                    val appContext: AppContext) extends ServicesConfig {

  def registerPublishCallback(): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    serviceLocatorConnector.subscribe(Subscription(appContext.appName, appContext.publisherUrl, Some(Map("third-party-api" -> "true"))))
  }

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration
}
