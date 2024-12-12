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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{verify => verifyStub, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.AsyncHmrcSpec

import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

class TpaConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {

  val tpaPort        = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val tpaHost        = "localhost"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(tpaPort))

  trait Setup {
    SharedMetricRegistries.clear()
    WireMock.reset()
    val tpaConfig = TpaConnector.Config("http://localhost:21112")

    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val appConfig: Configuration = mock[Configuration]

    val connector = new TpaConnector(tpaConfig, app.injector.instanceOf[HttpClientV2])

    val apiContext        = "test/hello"
    val apiVersion        = "1.0"
    val url               = s"/apis/$apiContext/versions/$apiVersion/subscribers"
    val errorResponseBody = """{"code":"INTERNAL_ERROR","message":"Something is wrong"}"""
  }

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(tpaHost, tpaPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  "deleteSubscriptions" should {

    "Delete any subscriptions for given context and version from tpa" in new Setup {
      stubFor(delete(urlEqualTo(url)).willReturn(aResponse()))

      await(connector.deleteSubscriptions(apiContext, apiVersion))

      verifyStub(deleteRequestedFor(urlEqualTo(url)))

    }

    "Fail if the tpa endpoint returns 500" in new Setup {
      stubFor(delete(urlEqualTo(url)).willReturn(
        aResponse()
          .withStatus(INTERNAL_SERVER_ERROR)
          .withBody(errorResponseBody)
      ))

      val caught = intercept[UpstreamErrorResponse] {
        await(connector.deleteSubscriptions(apiContext, apiVersion))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
      caught.message should include(errorResponseBody)
    }
  }
}
