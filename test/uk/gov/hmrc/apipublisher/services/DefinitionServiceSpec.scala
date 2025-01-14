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
import scala.concurrent.Future.{failed, successful}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import utils.AsyncHmrcSpec

import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnectorMockModule
import uk.gov.hmrc.apipublisher.models.oas.Endpoint
import uk.gov.hmrc.apipublisher.models.{DefinitionFileNoBodyReturned, ProducerApiDefinition, ServiceLocation}

class DefinitionServiceSpec extends AsyncHmrcSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup
      extends MicroserviceConnectorMockModule
      with MockitoSugar
      with ArgumentMatchersSugar {

    val oasVDS  = mock[OasVersionDefinitionService]
    val service = new DefinitionService(MicroserviceConnectorMock.aMock, oasVDS)

    val helloEndpoint   = Endpoint("/hello", "Say Hello", "GET", "USER", "UNLIMITED", Some("read:hello"), None)
    val goodbyeEndpoint = Endpoint("/goodbye", "Say Goodbye", "GET", "USER", "UNLIMITED", Some("read:hello"), None)

    val aServiceLocation = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]

    def primeOasFor(version: String, endpoints: Endpoint*) = {
      when(oasVDS.getDetailForVersion(*, *, eqTo(version))).thenReturn(successful(endpoints.toList))
    }

    def primeOasOnlyFor(version: String, endpoints: Endpoint*) = {
      primeOasFor(version, endpoints: _*)
    }

    def primeOasFailure(version: String, throwable: Throwable) = {
      when(oasVDS.getDetailForVersion(*, *, eqTo(version))).thenReturn(failed(throwable))
    }
  }

  "getDefinition" should {
    "handle no producer api definition for service location" in new Setup {
      MicroserviceConnectorMock.GetProducerApiDefinition.findsNone(aServiceLocation)

      await(service.getDefinition(aServiceLocation)).left.value shouldBe DefinitionFileNoBodyReturned(aServiceLocation)
    }

    "handle producer api definition with no data" in new Setup {
      val api = json[JsObject]("/input/api-with-one-version.json")
      MicroserviceConnectorMock.GetProducerApiDefinition.returns(ProducerApiDefinition(api))

      primeOasFor("1.0")

      intercept[IllegalStateException] {
        await(service.getDefinition(aServiceLocation))
      }
        .getMessage shouldBe "No endpoints defined for 1.0 of test"
    }

    "handle producer api definition with bad OAS" in new Setup {
      val api = json[JsObject]("/input/api-with-one-version.json")
      MicroserviceConnectorMock.GetProducerApiDefinition.returns(ProducerApiDefinition(api))

      primeOasFailure("1.0", new RuntimeException("Boom"))

      intercept[IllegalStateException] {
        await(service.getDefinition(aServiceLocation))
      }
        .getMessage startsWith "No endpoints defined for 1.0 of test due to failure in OAS Parsing"
    }

    "handle producer api definition with OAS data" in new Setup {
      val api = json[JsObject]("/input/api-with-one-version.json")
      MicroserviceConnectorMock.GetProducerApiDefinition.returns(ProducerApiDefinition(api))

      primeOasOnlyFor("1.0", helloEndpoint)

      val result = await(service.getDefinition(aServiceLocation))

      val expected = json[JsObject]("/expected/api-simple-oas.json")
      result.value shouldBe ProducerApiDefinition(expected)
    }
  }
}
