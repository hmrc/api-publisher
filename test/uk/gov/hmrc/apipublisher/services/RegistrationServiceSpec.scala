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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.apipublisher.config.AppContext
import uk.gov.hmrc.apipublisher.connectors.ServiceLocatorConnector
import uk.gov.hmrc.apipublisher.models.Subscription
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class RegistrationServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  trait Setup {
    val mockAppContext = mock[AppContext]
    val mockServiceLocatorConnector = mock[ServiceLocatorConnector]

    val appName = "Test Publisher Name"
    val publisherUrl = "http://testpublisher:8080/publish"
    when(mockAppContext.appName).thenReturn(appName)
    when(mockAppContext.publisherUrl).thenReturn(publisherUrl)

    val service = new RegistrationService(mockServiceLocatorConnector, mockAppContext)
  }

  "registerPublishCallback" should {
    "register to the publish callback with the service locator" in new Setup {
      givenConnectorReturns(service.serviceLocatorConnector, successful(()))

      val future = service.registerPublishCallback()

      whenReady(future) { _ =>
        verify(service.serviceLocatorConnector)
          .subscribe(ArgumentMatchers.eq(Subscription(appName, publisherUrl, Some(Map("third-party-api" -> "true")))))(any[HeaderCarrier])
      }
    }

    "fail when service register fails" in new Setup {
      val connectorResult = new RuntimeException("Error occurred")
      givenConnectorReturns(service.serviceLocatorConnector, failed(connectorResult))

      val future = service.registerPublishCallback()

      whenReady(future.failed) { ex =>
        ex shouldEqual connectorResult
      }
    }
  }

  def givenConnectorReturns(connector: ServiceLocatorConnector, result:Future[Unit]) = {
    when(connector.subscribe(any[Subscription])(any[HeaderCarrier])).thenReturn(result)
  }
}
