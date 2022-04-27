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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import utils.AsyncHmrcSpec

import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.loaders.ClasspathRamlLoader

import scala.util.Try
import uk.gov.hmrc.ramltools.domain.Endpoints
import uk.gov.hmrc.ramltools.domain.Endpoint
import uk.gov.hmrc.ramltools.domain.QueryParam
import scala.util.Failure
import uk.gov.hmrc.apipublisher.models.ServiceLocation

class RamlVersionDefinitionServiceSpec extends AsyncHmrcSpec {
  val testService = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val version = "1.0"
    val mockMicroserviceConnector = mock[MicroserviceConnector]
    val ramlVersionDefinitionService = new RamlVersionDefinitionService(mockMicroserviceConnector)

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]
    def raml(path: String): Try[RAML] = new ClasspathRamlLoader().load(path)
  }

  "The ramlVersionDefinitionService" should {
    "Fail if the microservice connector fails" in new Setup {
      val errorMessage = "something went wrong"
      when(mockMicroserviceConnector.getRaml(*,*)).thenReturn(Failure(new RuntimeException(errorMessage)))

      val exception: Exception = intercept[Exception] {
        await(ramlVersionDefinitionService.getDetailForVersion(testService, None, version))
      }

      exception.getMessage shouldBe errorMessage
    }

    "Create list of endpoints from a definition.json with no endpoints and a single raml version" in new Setup {
      val testRaml = raml("input/application.raml")
      when(mockMicroserviceConnector.getRaml(testService, version)).thenReturn(testRaml)

      val endpoints = await(ramlVersionDefinitionService.getDetailForVersion(testService, None, version))

      endpoints shouldBe List(Endpoint("/test/hello", "Say Hello", "GET", "USER", "UNLIMITED", Some("read:hello"), None))
    }

    "Create list of endpoints from a definition.json with no endpoints and dodgy context" in new Setup {
      val testRaml = raml("input/application.raml")
      when(mockMicroserviceConnector.getRaml(testService, version)).thenReturn(testRaml)

      val endpoints = await(ramlVersionDefinitionService.getDetailForVersion(testService, Some("xxx"), version))

      endpoints shouldBe List(Endpoint("/test/hello", "Say Hello", "GET", "USER", "UNLIMITED", Some("read:hello"), None))
    }

    "Create list of endpoints from a definition.json with no endpoints and throttlingTier annotation" in new Setup {
      val testRaml = raml("input/application-with-throttling.raml")
      when(mockMicroserviceConnector.getRaml(testService, version)).thenReturn(testRaml)

      val endpoints = await(ramlVersionDefinitionService.getDetailForVersion(testService, None, version))

      endpoints shouldBe List(Endpoint("/test/hello", "Say Hello", "GET", "USER", "LOW", Some("read:hello"), None))
    }

    "Create list of endpoints from a definition.json with no endpoints and a single raml version with an annotation library" in new Setup {
      val testRaml = raml("input/application-with-library.raml")
      when(mockMicroserviceConnector.getRaml(testService, version)).thenReturn(testRaml)

      val endpoints = await(ramlVersionDefinitionService.getDetailForVersion(testService, None, version))

      endpoints shouldBe List(Endpoint("/test/hello", "Say Hello", "GET", "USER", "UNLIMITED", Some("read:hello"), None))
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
