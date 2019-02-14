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

package uk.gov.hmrc.apipublisher.services

import org.mockito.BDDMockito.given
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json._
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ramltools.loaders.ClasspathRamlLoader
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext.Implicits.global

class DefinitionServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {
  val testService = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockMicroserviceConnector = mock[MicroserviceConnector]
    val definitionService = new DefinitionService(mockMicroserviceConnector)

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]
    def raml(path: String) = new ClasspathRamlLoader().load(path)
  }

  "The DefinitionService" should {
    "Create a ApiAndScopes object from a definition.json with no endpoints and a single raml version" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application.raml")

      val api_exp = json[JsObject]("/expected/api-simple.json")

      given(mockMicroserviceConnector.getAPIAndScopes(testService)).willReturn(successful(ApiAndScopes(api, scopes)))
      given(mockMicroserviceConnector.getRaml(testService, "1.0")).willReturn(testRaml)

      val expectedApiAndScopes = ApiAndScopes(api_exp, scopes)
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and no context" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_no_context.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application.raml")

      val api_exp = json[JsObject]("/expected/api-simple-no-context.json")

      given(mockMicroserviceConnector.getAPIAndScopes(testService)).willReturn(successful(ApiAndScopes(api, scopes)))
      given(mockMicroserviceConnector.getRaml(testService, "1.0")).willReturn(testRaml)

      val expectedApiAndScopes = ApiAndScopes(api_exp, scopes)
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and dodgy context" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_dodgy_context.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application.raml")

      val api_exp = json[JsObject]("/expected/api-simple-dodgy-context.json")

      given(mockMicroserviceConnector.getAPIAndScopes(testService)).willReturn(successful(ApiAndScopes(api, scopes)))
      given(mockMicroserviceConnector.getRaml(testService, "1.0")).willReturn(testRaml)

      val expectedApiAndScopes = ApiAndScopes(api_exp, scopes)
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and throttlingTier annotation" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application-with-throttling.raml")

      val api_exp = json[JsObject]("/expected/api-simple-throttling.json")

      given(mockMicroserviceConnector.getAPIAndScopes(testService)).willReturn(successful(ApiAndScopes(api, scopes)))
      given(mockMicroserviceConnector.getRaml(testService, "1.0")).willReturn(testRaml)

      val expectedApiAndScopes = ApiAndScopes(api_exp, scopes)
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and a single raml version with an annotation library" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application-with-library.raml")

      val api_exp = json[JsObject]("/expected/api-simple.json")

      given(mockMicroserviceConnector.getAPIAndScopes(testService)).willReturn(successful(ApiAndScopes(api, scopes)))
      given(mockMicroserviceConnector.getRaml(testService, "1.0")).willReturn(testRaml)

      val expectedApiAndScopes = ApiAndScopes(api_exp, scopes)
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and multiple raml versions" in new Setup {
      val api = json[JsObject]("/input/api-multi-version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml_1 = raml("input/application.raml")
      val testRaml_2 = raml("input/application-2.0.raml")
      val testRaml_3 = raml("input/application-3.0.raml")

      val api_exp = json[JsObject]("/expected/api-multi-version.json")

      given(mockMicroserviceConnector.getAPIAndScopes(testService)).willReturn(successful(ApiAndScopes(api, scopes)))
      given(mockMicroserviceConnector.getRaml(testService, "1.0")).willReturn(testRaml_1)
      given(mockMicroserviceConnector.getRaml(testService, "2.0")).willReturn(testRaml_2)
      given(mockMicroserviceConnector.getRaml(testService, "3.0")).willReturn(testRaml_3)

      val expectedApiAndScopes = ApiAndScopes(api_exp, scopes)
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }
  }

  "populateVersionFromRaml" should {
    "Add in a simple GET endpoint - no context" in new Setup {

      val version = json[JsObject]("/input/version-1.json")
      val raml1_0 = raml("input/simple-hello.raml").get
      val expOutput = json[JsObject]("/expected/json-exp-1.json")

      val actual = definitionService.populateVersionFromRaml(version, raml1_0, None)
      actual shouldBe expOutput
    }

    "Add in a simple GET endpoint with context" in new Setup {

      val version = json[JsObject]("/input/version-1.json")
      val raml1_0 = raml("input/simple-hello.raml").get
      val expOutput = json[JsObject]("/expected/json-exp-2.json")

      val actual = definitionService.populateVersionFromRaml(version, raml1_0, Some("test"))
      actual shouldBe expOutput
    }

    "Add in a GET endpoint with context and query parameters" in new Setup {

      val version = json[JsObject]("/input/version-1.json")
      val raml1_0 = raml("input/simple-hello-with-params.raml").get
      val expOutput = json[JsObject]("/expected/json-exp-3.json")

      val actual = definitionService.populateVersionFromRaml(version, raml1_0, Some("test"))
      actual shouldBe expOutput
    }
  }
}
