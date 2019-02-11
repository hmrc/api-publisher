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
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.{CONTENT_TYPE, JSON}
import uk.gov.hmrc.apipublisher.models
import uk.gov.hmrc.apipublisher.models.{ApiFieldDefinitions, FieldDefinition}
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.io.Source.fromURL

class APISubscriptionFieldsConnectorSpec extends UnitSpec with BeforeAndAfterAll with BeforeAndAfterEach
  with GuiceOneAppPerSuite with MockitoSugar {

  val apiSubscriptionFieldsPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiSubscriptionFieldsHost = "localhost"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiSubscriptionFieldsPort))

  val apiContext = "some/api/context"
  val urlEncodedApiContext = "some%2Fapi%2Fcontext"

  val version1 = "1.0"
  val version2 = "2.0"
  val fieldDefinitionsJString1 = fromURL(getClass.getResource("/input/field-definitions_1.json")).mkString
  val fieldDefinitionsJString2 = fromURL(getClass.getResource("/input/field-definitions_2.json")).mkString
  val apiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
    models.ApiFieldDefinitions(apiContext, version1, (Json.parse(fieldDefinitionsJString1) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, version2, (Json.parse(fieldDefinitionsJString2) \ "fieldDefinitions").as[Seq[FieldDefinition]]))

  val subscriptionFieldsUrl1 = s"/definition/context/$urlEncodedApiContext/version/$version1"
  val subscriptionFieldsUrl2 = s"/definition/context/$urlEncodedApiContext/version/$version2"

  val error500ResponseBody = """{"code":"INTERNAL_ERROR","message":"Something went really wrong"}"""

  trait Setup {
    val serviceConfig = mock[ServicesConfig]
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val appConfig: Configuration = mock[Configuration]

    val connector = new APISubscriptionFieldsConnector(serviceConfig, app.injector.instanceOf[HttpClient]) {
      override lazy val serviceBaseUrl = s"http://$apiSubscriptionFieldsHost:$apiSubscriptionFieldsPort"
    }

    def publishFieldDefinitions(definitions: Seq[ApiFieldDefinitions] = apiFieldDefinitions): Future[Unit] =
      connector.publishFieldDefinitions(definitions)
  }

  override protected def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(apiSubscriptionFieldsHost, apiSubscriptionFieldsPort)
  }

  override protected def beforeEach() {
    WireMock.reset()
    WireMock.resetAllRequests()
  }

  override protected def afterAll() {
    wireMockServer.stop()
  }

  "publishFieldDefinitions" should {

    "publish the field definitions in api-subscription-fields Service" in new Setup {
      stubFor(put(urlEqualTo(subscriptionFieldsUrl1)).willReturn(aResponse()))
      stubFor(put(urlEqualTo(subscriptionFieldsUrl2)).willReturn(aResponse()))

      await(publishFieldDefinitions())

      verify(putRequestedFor(urlEqualTo(subscriptionFieldsUrl1))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(fieldDefinitionsJString1)))
      verify(putRequestedFor(urlEqualTo(subscriptionFieldsUrl2))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(fieldDefinitionsJString2)))

      getAllWiremockRequests should have size 2
    }

    "don't call api-subscription-fields Service when there's no field definitions to publish" in new Setup {
      await(publishFieldDefinitions(definitions = Nil))

      getAllWiremockRequests shouldBe 'empty
    }

    "return a failed future with response body if the api-subscription-fields endpoint returns 500" in new Setup {
      stubFor(put(urlEqualTo(subscriptionFieldsUrl1)).willReturn(aResponse()))
      stubFor(put(urlEqualTo(subscriptionFieldsUrl2)).willReturn(
        aResponse().withStatus(Status.INTERNAL_SERVER_ERROR).withBody(error500ResponseBody)))

      val caught = intercept[Upstream5xxResponse] {
        await(publishFieldDefinitions())
      }

      caught.message should include (error500ResponseBody)
    }
  }

  private def getAllWiremockRequests = {
    findAll(putRequestedFor(urlMatching(".*")))
  }
}
