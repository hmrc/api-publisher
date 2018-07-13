/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.apipublisher.services.RegistrationService

import scala.concurrent.Future.successful

class PublisherGlobalSpec extends UnitSpec with MockitoSugar with ScalaFutures with OneAppPerSuite {

  val mockRegistrationService = mock[RegistrationService]
  when(mockRegistrationService.registerPublishCallback()).thenReturn(successful(()))

  override implicit lazy val app: Application = GuiceApplicationBuilder()
      .overrides(bind[RegistrationService].toInstance(mockRegistrationService)).build()

  "Publisher" should {
    "Register the publish callback with the service locator on startup" in {
      verify(mockRegistrationService).registerPublishCallback()
    }
  }
}
