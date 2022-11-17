/*
 * Copyright 2022 HM Revenue & Customs
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

import utils.AsyncHmrcSpec
import org.mockito.{MockitoSugar, ArgumentMatchersSugar}
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnectorMockModule
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apipublisher.models.ServiceLocation
import uk.gov.hmrc.http.HeaderCarrier
import play.api.libs.json._
import uk.gov.hmrc.apipublisher.models.ApiAndScopes
import uk.gov.hmrc.ramltools.domain.Endpoint
import scala.concurrent.Future.successful

class DefinitionServiceSpec extends AsyncHmrcSpec {
  
  implicit val hc = HeaderCarrier()

  trait Setup 
      extends MicroserviceConnectorMockModule 
      with MockitoSugar 
      with ArgumentMatchersSugar {

    val ramlVDS = mock[RamlVersionDefinitionService]
    val oasVDS = mock[OasVersionDefinitionService]
    val service = new DefinitionService(MicroserviceConnectorMock.aMock, ramlVDS, oasVDS)

    val helloEndpoint = Endpoint("/hello", "Say Hello", "GET", "USER", "UNLIMITED", Some("read:hello"), None)
    val goodbyeEndpoint = Endpoint("/goodbye", "Say Goodbye", "GET", "USER", "UNLIMITED", Some("read:hello"), None)

    val aServiceLocation = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]

    def primeRamlFor(version: String, endpoints: Endpoint*) = {
      when(ramlVDS.getDetailForVersion(*, *, eqTo(version))).thenReturn(successful(endpoints.toList))
    }

    def primeOasFor(version: String, endpoints: Endpoint*) = {
      when(oasVDS.getDetailForVersion(*, *, eqTo(version))).thenReturn(successful(endpoints.toList))
    }

    def primeRamlOnlyFor(version: String, endpoints: Endpoint*) = {
      primeRamlFor(version, endpoints: _*)
      primeOasFor(version)
    }

    def primeOasOnlyFor(version: String, endpoints: Endpoint*) = {
      primeRamlFor(version)
      primeOasFor(version, endpoints: _*)
    }
  }

  "getDefinition" should {
    "handle no api and scopes for service location" in new Setup {
      MicroserviceConnectorMock.GetAPIAndScopes.findsNone

      val result = await(service.getDefinition(aServiceLocation))

      result shouldBe None
    }  

    "handle api and scopes with no data" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      primeRamlFor("1.0")
      primeOasFor("1.0")

      intercept[IllegalStateException] {
        await(service.getDefinition(aServiceLocation))
      }
      .getMessage startsWith "No endpoints defined for"
    }

    "handle api and scopes with RAML data only" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      primeRamlOnlyFor("1.0", helloEndpoint)

      val result = await(service.getDefinition(aServiceLocation))

      val expected = json[JsObject]("/expected/api-simple-raml.json")
      result.value shouldBe ApiAndScopes(expected, scopes)
    }
  
    "handle api and scopes with OAS data only" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      primeOasOnlyFor("1.0", helloEndpoint)

      val result = await(service.getDefinition(aServiceLocation))

      val expected = json[JsObject]("/expected/api-simple-oas.json")
      result.value shouldBe ApiAndScopes(expected, scopes)
    }
  
    "handle api and scopes with both RAML and OS data that matches" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      primeOasFor("1.0", helloEndpoint, goodbyeEndpoint)
      primeRamlFor("1.0", helloEndpoint, goodbyeEndpoint)

      val result = await(service.getDefinition(aServiceLocation))

      val expected = json[JsObject]("/expected/api-simple-hello-goodbye.json")
      result.value shouldBe ApiAndScopes(expected, scopes)
    }

    "handle api and scopes with both RAML and OS data that matches except for ordering" in new Setup {
       val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      primeOasFor("1.0", helloEndpoint, goodbyeEndpoint)
      primeRamlFor("1.0", goodbyeEndpoint, helloEndpoint)

      val result = await(service.getDefinition(aServiceLocation))

      val expected = json[JsObject]("/expected/api-simple-hello-goodbye.json")
      result.value shouldBe ApiAndScopes(expected, scopes)
    }
    
    "handle api and scopes with both RAML and OAS data but that do not match by publishing RAML" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      primeOasFor("1.0", helloEndpoint.copy(authType = "OPEN"))
      primeRamlFor("1.0", helloEndpoint)

      val result = await(service.getDefinition(aServiceLocation))

      // Expectation matches RAML processing.
      val expected = json[JsObject]("/expected/api-simple-raml.json")
      result.value shouldBe ApiAndScopes(expected, scopes)
    }
  }
}
