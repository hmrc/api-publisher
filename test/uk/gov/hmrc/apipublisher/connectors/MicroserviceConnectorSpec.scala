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
import org.mockito.Mockito.{verify => verifyMock}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json.parse
import play.api.libs.json.{JsArray, JsObject}
import play.api.test.{FakeApplication, Helpers}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ramltools.loaders.RamlLoader

import scala.io.Source

class MicroserviceConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with MockitoSugar {

  val apiProducerPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiProducerHost = "127.0.0.1"
  val apiProducerUrl = s"http://$apiProducerHost:$apiProducerPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiProducerPort))

  val testService = ServiceLocation("test.example.com", apiProducerUrl)

  val apiAndScopeDefinition = Source.fromURL(getClass.getResource("/input/api-definition-without-endpoints.json")).mkString

  val api = parse(getClass.getResourceAsStream("/input/api-without-endpoints.json")).as[JsObject]
  val scopes = parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]

  trait Setup {
    val mockRamlLoader = mock[RamlLoader]
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val http = mock[HttpClient]
    val connector = new MicroserviceConnector(mockRamlLoader, http)
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiProducerHost, apiProducerPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "getAPIDefinition" should {
      "Return the api definition" in new Setup {

        Helpers.running(FakeApplication()) {

          stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinition)))

          val result = await(connector.getAPIAndScopes(testService))

          result shouldEqual ApiAndScopes(api, scopes)
        }
    }

    "Fail if the API endpoint returns 404" in new Setup {

      Helpers.running(FakeApplication()) {

        stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(Status.NOT_FOUND)))

        intercept[NotFoundException] {
          await(connector.getAPIAndScopes(testService))
        }
      }
    }

    "should not parse nginx response to JSON" in new Setup {

      Helpers.running(FakeApplication()) {

        val badGatewayResponse = """<html>
                                   |<head><title>502 Bad Gateway</title></head>
                                   |<body bgcolor="white">
                                   |<center><h1>502 Bad Gateway</h1></center>
                                   |<hr><center>nginx</center>
                                   |</body>
                                   |</html>""".stripMargin

        stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(Status.BAD_GATEWAY).withBody(badGatewayResponse)))

        val badGatewayException = intercept[Upstream5xxResponse]{
          await(connector.getAPIAndScopes(testService))
        }

        badGatewayException.getMessage should include ("<head><title>502 Bad Gateway</title></head>")
      }
    }

    "should call the microservice to get the application.raml" in new Setup {
      connector.getRaml(testService, "1.0")

      verifyMock(mockRamlLoader).load(testService.serviceUrl + "/api/conf/1.0/application.raml")
    }
  }
}
