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

package uk.gov.hmrc.apipublisher.services

import scala.concurrent.ExecutionContext.Implicits.global

import io.swagger.v3.oas.models.OpenAPI
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import utils.AsyncHmrcSpec

import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ramltools.domain.Endpoint

import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnectorMockModule
import uk.gov.hmrc.apipublisher.models.ServiceLocation

class OasVersionDefinitionServiceSpec extends AsyncHmrcSpec {
  val aServiceLocation           = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup
      extends MicroserviceConnectorMockModule
      with MockitoSugar
      with ArgumentMatchersSugar {

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]

    val mockParser = mock[OasVersionDefinitionService.OasParser]
    val context    = None
    val version    = "1.0"
    val service    = new OasVersionDefinitionService(MicroserviceConnectorMock.aMock, mockParser)
  }

  "OasDefinitionService" should {
    "handle if microservice has no OAS" in new Setup {
      MicroserviceConnectorMock.GetOAS.findsNoValidFile

      intercept[RuntimeException] {
        await(service.getDetailForVersion(aServiceLocation, context, version))
      }
    }

    "returns none if that OAS does not contain the version" in new Setup {
      val openAPI = mock[OpenAPI]
      MicroserviceConnectorMock.GetOAS.findsValid(openAPI)
      when(mockParser.apply(context)(openAPI)).thenReturn(List.empty)

      val result = await(service.getDetailForVersion(aServiceLocation, context, "99.99"))
      result shouldBe List.empty
    }

    "returns the endpoints for the version" in new Setup {
      val openAPI = mock[OpenAPI]
      MicroserviceConnectorMock.GetOAS.findsValid(openAPI)
      when(mockParser.apply(context)(openAPI)).thenReturn(List(Endpoint("u", "e", "m", "a", "t", None, None)))

      val result = await(service.getDetailForVersion(aServiceLocation, context, "99.99"))
      result should have size (1)
    }
  }
}
