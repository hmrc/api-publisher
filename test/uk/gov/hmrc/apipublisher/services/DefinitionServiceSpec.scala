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

import play.api.libs.json._
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import utils.AsyncHmrcSpec

import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.loaders.ClasspathRamlLoader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import scala.util.Try
import uk.gov.hmrc.ramltools.domain.Endpoints
import uk.gov.hmrc.ramltools.domain.Endpoint
import uk.gov.hmrc.ramltools.domain.QueryParam

class DefinitionServiceSpec extends AsyncHmrcSpec {
  val testService = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockMicroserviceConnector = mock[MicroserviceConnector]
    val definitionService = new DefinitionService(mockMicroserviceConnector)

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]
    def raml(path: String): Try[RAML] = new ClasspathRamlLoader().load(path)
  }

  "The DefinitionService" should {
    "Return none if the microservice connector returns none" in new Setup {
      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(None))

      val definition: Option[ApiAndScopes] = await(definitionService.getDefinition(testService))

      definition shouldBe None
    }

    "Fail if the microservice connector fails" in new Setup {
      val errorMessage = "something went wrong"
      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(failed(new RuntimeException(errorMessage)))

      val exception: Exception = intercept[Exception] {
        await(definitionService.getDefinition(testService))
      }

      exception.getMessage shouldBe errorMessage
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and a single raml version" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application.raml")

      val api_exp = json[JsObject]("/expected/api-simple.json")

      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(Some(ApiAndScopes(api, scopes))))
      when(mockMicroserviceConnector.getRaml(testService, "1.0")).thenReturn(testRaml)

      val expectedApiAndScopes = Some(ApiAndScopes(api_exp, scopes))
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and no context" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_no_context.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application.raml")

      val api_exp = json[JsObject]("/expected/api-simple-no-context.json")

      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(Some(ApiAndScopes(api, scopes))))
      when(mockMicroserviceConnector.getRaml(testService, "1.0")).thenReturn(testRaml)

      val expectedApiAndScopes = Some(ApiAndScopes(api_exp, scopes))
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and dodgy context" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_dodgy_context.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application.raml")

      val api_exp = json[JsObject]("/expected/api-simple-dodgy-context.json")

      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(Some(ApiAndScopes(api, scopes))))
      when(mockMicroserviceConnector.getRaml(testService, "1.0")).thenReturn(testRaml)

      val expectedApiAndScopes = Some(ApiAndScopes(api_exp, scopes))
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and throttlingTier annotation" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application-with-throttling.raml")

      val api_exp = json[JsObject]("/expected/api-simple-throttling.json")

      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(Some(ApiAndScopes(api, scopes))))
      when(mockMicroserviceConnector.getRaml(testService, "1.0")).thenReturn(testRaml)

      val expectedApiAndScopes = Some(ApiAndScopes(api_exp, scopes))
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }

    "Create a ApiAndScopes object from a definition.json with no endpoints and a single raml version with an annotation library" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      val testRaml = raml("input/application-with-library.raml")

      val api_exp = json[JsObject]("/expected/api-simple.json")

      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(Some(ApiAndScopes(api, scopes))))
      when(mockMicroserviceConnector.getRaml(testService, "1.0")).thenReturn(testRaml)

      val expectedApiAndScopes = Some(ApiAndScopes(api_exp, scopes))
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

      when(mockMicroserviceConnector.getAPIAndScopes(testService)).thenReturn(successful(Some(ApiAndScopes(api, scopes))))
      when(mockMicroserviceConnector.getRaml(testService, "1.0")).thenReturn(testRaml_1)
      when(mockMicroserviceConnector.getRaml(testService, "2.0")).thenReturn(testRaml_2)
      when(mockMicroserviceConnector.getRaml(testService, "3.0")).thenReturn(testRaml_3)

      val expectedApiAndScopes = Some(ApiAndScopes(api_exp, scopes))
      val definition = await(definitionService.getDefinition(testService))

      definition shouldBe expectedApiAndScopes
    }
  }

  "Create Endpoint objects" should {
    "simple GET endpoint - no context" in new Setup {
      val raml1_0 = raml("input/simple-hello.raml").get
      val context = None
      val endpoints = Endpoints(raml1_0, context)

      endpoints should contain only Endpoint(
        uriPattern = "/test/hello",
        endpointName = "Say Hello",
        method = "GET",
        authType = "NONE",
        throttlingTier = "UNLIMITED",
        scope = None,
        queryParameters = None
      )
    }

    "simple GET endpoint with context" in new Setup {
      val raml1_0 = raml("input/simple-hello.raml").get
      val context = Some("test")
      val endpoints = Endpoints(raml1_0, context)

      endpoints should contain only Endpoint(
        uriPattern = "/hello",
        endpointName = "Say Hello",
        method = "GET",
        authType = "NONE",
        throttlingTier = "UNLIMITED",
        scope = None,
        queryParameters = None
      )
    }

    "GET endpoint with context and query parameters" in new Setup {
      val raml1_0 = raml("input/simple-hello-with-params.raml").get
      val context = Some("test")
      val endpoints = Endpoints(raml1_0, context)

      endpoints should contain only Endpoint(
        uriPattern = "/hello",
        endpointName = "Say Hello",
        method = "GET",
        authType = "NONE",
        throttlingTier = "UNLIMITED",
        scope = None,
        queryParameters = Some(Seq(QueryParam("name", true), QueryParam("desc", false)))
      )
    }
  }
}
