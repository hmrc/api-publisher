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
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication
import play.api.test.Helpers.{CONTENT_TYPE, JSON, running}
import uk.gov.hmrc.apipublisher.config.WSHttp
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.play.config.inject.DefaultServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.io.Source.fromURL

class APIDefinitionConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with MockitoSugar {

  val apiDefinitionPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiDefinitionHost = "localhost"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDefinitionPort))

  val definition = fromURL(getClass.getResource("/input/api-with-endpoints.json")).mkString
  val api = Json.parse(definition).as[JsObject]

  trait Setup {
    val serviceConfig = mock[DefaultServicesConfig]
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val http = new WSHttp {
      override val hooks = Seq()
    }
    val connector = new APIDefinitionConnector(serviceConfig, http) {
      override lazy val serviceBaseUrl = "http://localhost:21112"
    }
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiDefinitionHost, apiDefinitionPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "publishAPI" should {

    "Publish the API in api-definition Service" in new Setup {

      running(FakeApplication()) {

        stubFor(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

        await(connector.publishAPI(api))

        verify(postRequestedFor(urlEqualTo("/api-definition"))
          .withHeader(CONTENT_TYPE, containing(JSON))
          .withRequestBody(equalToJson(definition)))
      }

    }

    "Fail if the api-definition endpoint returns 500" in new Setup {

      running(FakeApplication()) {

        stubFor(post(urlEqualTo("/api-definition")).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR)))

        intercept[Exception] {
          await(connector.publishAPI(api))
        }

      }

    }
  }

  "Validate api" should {

    "add dummy serviceBaseUrl and serviceName" in new Setup {
      running(FakeApplication()) {
        stubFor(post(urlEqualTo("/api-definition/validate")).willReturn(aResponse().withStatus(Status.NO_CONTENT)))

        await(connector.validateAPIDefinition(api))

        val expected: JsObject = api.as[JsObject] ++ Json.obj("serviceBaseUrl" -> "dummy", "serviceName" -> "dummy")
        verify(postRequestedFor(urlPathEqualTo("/api-definition/validate")).withRequestBody(equalToJson(expected.toString())))
      }
    }
  }


}
