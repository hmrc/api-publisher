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
import uk.gov.hmrc.apipublisher.connectors.ServiceLocatorConnector
import uk.gov.hmrc.apipublisher.models.{Registration, Subscription}
import uk.gov.hmrc.apipublisher.wiring.AppContext
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class RegistrationService @Inject()(val serviceLocatorConnector: ServiceLocatorConnector,
                                    val appContext: AppContext) {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def register() = {
    if (appContext.registrationEnabled) {
      serviceLocatorConnector.register(Registration(appContext.appName, appContext.appUrl, Some(Map("third-party-api" -> "true"))))
    } else {
      Future.successful()
    }
  }

  def subscribeToPublishCallback() = {
    serviceLocatorConnector.subscribe(Subscription(appContext.appName, appContext.publisherUrl, Some(Map("third-party-api" -> "true"))))
  }
}
