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
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.api.test.Helpers.{CONTENT_TYPE, JSON, running}
import uk.gov.hmrc.apipublisher.config.AuditedWSHttp
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

class APIScopeConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with MockitoSugar {

  val apiScopePort = sys.env.getOrElse("WIREMOCK", "21113").toInt
  val apiScopeHost = "localhost"
  val apiScopeUrl = s"http://$apiScopeHost:$apiScopePort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiScopePort))

  val scopes = Json.parse(getClass.getResourceAsStream("/input/scopes.json"))

  trait Setup {
    val serviceConfig = mock[ServicesConfig]
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val http = AuditedWSHttp

    val connector = new APIScopeConnector(serviceConfig, http) {
      override lazy val serviceBaseUrl: String = "http://localhost:21113"
    }
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiScopeHost, apiScopePort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "publishScopes" should {
    "Publish the scopes" in new Setup {

      running(FakeApplication()) {

        stubFor(post(urlEqualTo("/scope")).willReturn(aResponse()))

        await(connector.publishScopes(scopes))

        verify(postRequestedFor(urlEqualTo("/scope"))
          .withHeader(CONTENT_TYPE, containing(JSON))
          .withRequestBody(equalTo(scopes.toString())))
      }
    }

    "Fail if the api-scope endpoint returns 500" in new Setup {
      running(FakeApplication()) {

        stubFor(post(urlEqualTo("/scope")).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR)))

        val caught = intercept[Exception] {
          await(connector.publishScopes(scopes))
        }
        assert(caught.isInstanceOf[Upstream5xxResponse])
        assert(caught.getMessage.contains("/scope' returned 500"))
      }
    }
  }
}
