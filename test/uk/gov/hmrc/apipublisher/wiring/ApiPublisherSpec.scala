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

import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.apipublisher.services.RegistrationService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class ApiPublisherSpec extends UnitSpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  val mockRegistrationService = mock[RegistrationService]
  when(mockRegistrationService.subscribeToPublishCallback()).thenReturn(successful(()))
  when(mockRegistrationService.register()).thenReturn(successful(()))

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .configure("Test.microservice.services.service-locator.enabled" -> true)
    .overrides(bind[RegistrationService].toInstance(mockRegistrationService))
    .build()

  "Publisher" should {

    "Subscribe to the publish callback then afterwards register the API with the service locator on startup" in {
      val orderedVerifier = Mockito.inOrder(mockRegistrationService)
      val millisecondsToWaitForSecondCall = 5000
      verify(mockRegistrationService).subscribeToPublishCallback()
      verify(mockRegistrationService, Mockito.timeout(millisecondsToWaitForSecondCall)).register()
      orderedVerifier.verify(mockRegistrationService).subscribeToPublishCallback()
      orderedVerifier.verify(mockRegistrationService).register()
    }
  }
}
