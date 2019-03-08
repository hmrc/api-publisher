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

package uk.gov.hmrc.apipublisher.connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.{CONTENT_TYPE, JSON}
import uk.gov.hmrc.apipublisher.models.{Registration, Subscription}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ServiceLocatorConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with MockitoSugar with GuiceOneServerPerSuite {

  val serviceLocatorPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val serviceLocatorHost = "localhost"
  val serviceLocatorUrl = s"http://$serviceLocatorHost:$serviceLocatorPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(serviceLocatorPort))

  val subscription = Subscription("subPublisherName", "http://subPublisherUri")
  val registration = Registration("regPublisherName", "http://regPublisherUri")

  trait Setup {
    val serviceLocatorConfig = ServiceLocatorConfig(s"$serviceLocatorUrl")
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val appConfig: Configuration = mock[Configuration]

    val connector = new ServiceLocatorConnector(serviceLocatorConfig, app.injector.instanceOf[HttpClient])
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(serviceLocatorHost, serviceLocatorPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "Subscribe" should {

    "Post a subscription" in new Setup {
      stubFor(post(urlEqualTo("/subscription")).willReturn(aResponse().withStatus(Status.NO_CONTENT)))

      await(connector.subscribe(subscription))

      verify(postRequestedFor(urlEqualTo("/subscription"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(Json.toJson(subscription).toString())))

    }

    "Ignore when service locator return an error code" in new Setup {
      stubFor(post(urlEqualTo("/subscription")).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR)))

      connector.subscribe(subscription)
    }
  }

  "Register" should {

    "Post a registration" in new Setup {
      stubFor(post(urlEqualTo("/registration")).willReturn(aResponse().withStatus(Status.NO_CONTENT)))

      await(connector.register(registration))

      verify(postRequestedFor(urlEqualTo("/registration"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(Json.toJson(registration).toString())))

    }

    "Ignore when service locator return an error code" in new Setup {
      stubFor(post(urlEqualTo("/registration")).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR)))

      connector.subscribe(subscription)
    }
  }

}