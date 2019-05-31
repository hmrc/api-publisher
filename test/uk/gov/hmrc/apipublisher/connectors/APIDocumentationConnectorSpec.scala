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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.{CONTENT_TYPE, JSON}
import uk.gov.hmrc.apipublisher.models.RegistrationRequest
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class APIDocumentationConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with MockitoSugar with GuiceOneAppPerSuite {

  val apiDocumentationPort: Int = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiDocumentationHost = "localhost"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDocumentationPort))

  val registrationRequest = RegistrationRequest("api-example-microservice", "http://localhost", Seq("1.0", "2.0"))

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val apiDocumentationConfig = ApiDocumentationConfig("http://localhost:21112")
    val connector = new APIDocumentationConnector(apiDocumentationConfig, app.injector.instanceOf[HttpClient])
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiDocumentationHost, apiDocumentationPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "registerService" should {
    "Register the service with API Documentation" in new Setup {
      stubFor(post(urlEqualTo("/apis/register")).willReturn(aResponse().withStatus(Status.ACCEPTED)))

      await(connector.registerService(registrationRequest))

      verify(postRequestedFor(urlEqualTo("/apis/register"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalTo(Json.toJson(registrationRequest).toString)))
    }

    "Fail if API Documentation returns 500" in new Setup {
      stubFor(post(urlEqualTo("/apis/register")).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR)))

      val caught: Exception = intercept[Exception] {
        await(connector.registerService(registrationRequest))
      }
      assert(caught.isInstanceOf[Upstream5xxResponse])
      assert(caught.getMessage.contains("/apis/register' returned 500"))
    }
  }
}
