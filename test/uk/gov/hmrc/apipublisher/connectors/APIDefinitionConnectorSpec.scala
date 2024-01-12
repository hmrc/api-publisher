/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source.fromURL

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{verify => verifyStub, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.AsyncHmrcSpec

import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{CONTENT_TYPE, JSON, _}
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

class APIDefinitionConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {

  val apiDefinitionPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiDefinitionHost = "localhost"
  val wireMockServer    = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDefinitionPort))

  val definition = fromURL(getClass.getResource("/input/api-with-endpoints.json")).mkString
  val api        = Json.parse(definition).as[JsObject]

  trait Setup {
    SharedMetricRegistries.clear()
    WireMock.reset()
    val apiDefinitionConfig = ApiDefinitionConfig("http://localhost:21112")

    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val appConfig: Configuration = mock[Configuration]

    val connector = new APIDefinitionConnector(apiDefinitionConfig, app.injector.instanceOf[HttpClient])
  }

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(apiDefinitionHost, apiDefinitionPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  "publishAPI" should {

    "Publish the API in api-definition Service" in new Setup {
      stubFor(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      await(connector.publishAPI(api))

      verifyStub(postRequestedFor(urlEqualTo("/api-definition"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(definition)))

    }

    "Fail if the api-definition endpoint returns 500" in new Setup {
      stubFor(post(urlEqualTo("/api-definition")).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Exception] {
        await(connector.publishAPI(api))
      }
    }
  }

  "Validate api" should {

    "add dummy serviceBaseUrl and serviceName" in new Setup {
      stubFor(post(urlEqualTo("/api-definition/validate")).willReturn(aResponse().withStatus(NO_CONTENT)))

      await(connector.validateAPIDefinition(api))

      val expected: JsObject = api.as[JsObject] ++ Json.obj("serviceBaseUrl" -> "dummy", "serviceName" -> "dummy")
      verifyStub(postRequestedFor(urlPathEqualTo("/api-definition/validate")).withRequestBody(equalToJson(expected.toString())))
    }
  }
}
