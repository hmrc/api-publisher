/*
 * Copyright 2021 HM Revenue & Customs
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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{verify => verifyStub, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.apipublisher.models.Scope
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global

class APIScopeConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {
  SharedMetricRegistries.clear()


  val apiScopePort = sys.env.getOrElse("WIREMOCK", "21113").toInt
  val apiScopeHost = "localhost"
  val apiScopeUrl = s"http://$apiScopeHost:$apiScopePort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiScopePort))

  val scopes = Json.parse(getClass.getResourceAsStream("/input/scopes.json"))

  trait Setup {
    WireMock.reset()
    val apiScopeConfig = ApiScopeConfig("http://localhost:21113")

    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val connector = new APIScopeConnector(apiScopeConfig, app.injector.instanceOf[HttpClient])
  }

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(apiScopeHost, apiScopePort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  "publishScopes" should {
    "Publish the scopes" in new Setup {
      stubFor(post(urlEqualTo("/scope")).willReturn(aResponse()))

      await(connector.publishScopes(scopes))

      verifyStub(postRequestedFor(urlEqualTo("/scope"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalTo(scopes.toString())))
    }

    "Fail if the api-scope endpoint returns 500" in new Setup {
      stubFor(post(urlEqualTo("/scope")).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      val caught = intercept[UpstreamErrorResponse] {
        await(connector.publishScopes(scopes))
      }
      
      assert(caught.statusCode == INTERNAL_SERVER_ERROR)
      assert(caught.getMessage.contains("/scope' returned 500"))
    }
  }

  "retrieveScopes" should {
    "retrieve scopes when one search key is provided" in new Setup {
      val scopeKeys = Set("akey")
      val urlToCall = "/scope?keys=akey"
      stubFor(get(urlEqualTo(urlToCall))
        .willReturn(aResponse().withBody(scopes.toString())))

      val result: Seq[Scope] = await(connector.retrieveScopes(scopeKeys))

      verifyStub(getRequestedFor(urlEqualTo(urlToCall)))
      result shouldEqual scopes.as[Seq[Scope]]

    }

    "retrieve scopes when multiple search keys are provided" in new Setup {
      val scopeKeys = Set("akey", "anotherKey", "oneMoreForLuck")
      val urlToCall = s"/scope?keys=${scopeKeys.mkString("+")}"
      stubFor(get(urlEqualTo(urlToCall))
        .willReturn(aResponse().withBody(scopes.toString())))

      val result: Seq[Scope] = await(connector.retrieveScopes(scopeKeys))

      verifyStub(getRequestedFor(urlEqualTo(urlToCall)))
      result shouldEqual scopes.as[Seq[Scope]]
    }
  }
}
