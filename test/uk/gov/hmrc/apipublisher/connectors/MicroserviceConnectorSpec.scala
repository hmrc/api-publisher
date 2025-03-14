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
import scala.concurrent.duration._
import scala.io.Source

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.swagger.v3.parser.OpenAPIV3Parser
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.AsyncHmrcSpec

import play.api.libs.json.Json.parse
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apipublisher.models.APICategory.{CUSTOMS, EXAMPLE, OTHER}
import uk.gov.hmrc.apipublisher.models._

class MicroserviceConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {

  SharedMetricRegistries.clear()
  val apiProducerPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiProducerHost = "127.0.0.1"
  val apiProducerUrl  = s"http://$apiProducerHost:$apiProducerPort"
  val wireMockServer  = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiProducerPort))

  val testService = ServiceLocation("test.example.com", apiProducerUrl)

  val producerApiDefinition = handleGetFileAndClose("/input/valid-api-definition.json")

  val invalidContextInDefinition           = handleGetFileAndClose("/input/invalid-context-in-api-definition.json")
  val invalidDefinitionWithEmptyScopes     = handleGetFileAndClose("/input/api-definition-no-scopes-defined.json")
  val invalidDefinitionWithScopes          = handleGetFileAndClose("/input/invalid-api-definition-with-scopes.json")
  val invalidEndpointsInDefinition         = handleGetFileAndClose("/input/invalid-api-definition-with-endpoints.json")
  val invalidWhitelistedAppIdsInDefinition = handleGetFileAndClose("/input/invalid-api-definition-with-whitelistedapplicationIds.json")
  val invalidPublishedStatusInDefinition   = handleGetFileAndClose("/input/invalid-api-definition-with-published-status.json")
  val invalidPrototypedStatusInDefinition  = handleGetFileAndClose("/input/invalid-api-definition-with-prototyped-status.json")

  val api = parse(getClass.getResourceAsStream("/input/valid-api.json")).as[JsObject]

  trait Setup {
    WireMock.reset()
    implicit val hc: HeaderCarrier   = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    implicit val system: ActorSystem = app.injector.instanceOf[ActorSystem]

    val appConfig: Configuration = mock[Configuration]

    lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockOasFileLoader,
      app.injector.instanceOf[HttpClientV2],
      app.injector.instanceOf[Environment]
    )

    val mockOasFileLoader = mock[OASFileLoader]
    val oasParser         = new OpenAPIV3Parser()
  }

  trait SetupWithNoApiDefinitionValidation extends Setup {

    override lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = false, oasParserMaxDuration = 3.seconds),
      mockOasFileLoader,
      app.injector.instanceOf[HttpClientV2],
      app.injector.instanceOf[Environment]
    )
  }

  trait SetupWithMockedOpenApiParser extends Setup {
    val mockOpenApiParser = mock[OpenAPIV3Parser]

    override lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockOasFileLoader,
      app.injector.instanceOf[HttpClientV2],
      app.injector.instanceOf[Environment]
    )
  }

  def handleGetFileAndClose(path: String) = {
    val definitionSource = Source.fromURL(getClass.getResource(path))
    val definitionStr    = definitionSource.mkString
    definitionSource.close()
    definitionStr
  }

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(apiProducerHost, apiProducerPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  "getAPIDefinition" should {
    "Return the api definition" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(producerApiDefinition)))

      await(connector.getProducerApiDefinition(testService)).value shouldBe ProducerApiDefinition(api)
    }

    "Default categories to OTHER when API is not in categories map" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(producerApiDefinition)))

      await(connector.getProducerApiDefinition(testService)).value.categories should contain only OTHER
    }

    "Not default categories when API is in categories map but categories is defined in the definition" in new Setup {
      val helloDefinition = handleGetFileAndClose("/input/hello-definition-with-categories.json")
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      await(connector.getProducerApiDefinition(testService)).value.categories should contain only CUSTOMS
    }

    "Default categories when API is in categories map and categories is missing from the definition" in new Setup {
      val helloDefinition = handleGetFileAndClose("/input/hello-definition-without-categories.json")
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      await(connector.getProducerApiDefinition(testService)).value.categories should contain only EXAMPLE
    }

    "Default categories when API is in categories map and categories is empty from the definition" in new Setup {
      val helloDefinition = handleGetFileAndClose("/input/hello-definition-with-empty-categories.json")
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      await(connector.getProducerApiDefinition(testService)).value.categories should contain only EXAMPLE
    }

    "Return DefinitionFileNoBodyReturned if the API endpoint returns 204" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NO_CONTENT)))

      await(connector.getProducerApiDefinition(testService)) match {
        case Left(DefinitionFileNoBodyReturned(_)) => succeed
        case _                                     => fail()
      }

    }

    "return DefinitionFileNotFound if the API endpoint returns 404" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = await(connector.getProducerApiDefinition(testService)).left.value
      result shouldBe DefinitionFileNotFound(
        ServiceLocation("test.example.com", "http://127.0.0.1:21112")
      )
    }

    "return DefinitionFileFailedSchemaValidation if context in API definition is invalid" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidContextInDefinition)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#/properties/api/properties/context","pointerToViolation":"#/api/context","causingExceptions":[],"keyword":"pattern","message":"string [t] does not match pattern ^[a-z]+[a-z/\\-]{4,}$"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has empty scopes" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinitionWithEmptyScopes)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#","pointerToViolation":"#","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [scopes] is not permitted"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has scopes" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinitionWithScopes)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#","pointerToViolation":"#","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [scopes] is not permitted"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has endpoints" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidEndpointsInDefinition)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#/properties/api/properties/versions/items","pointerToViolation":"#/api/versions/0","causingExceptions":[{"pointerToViolation":"#/api/versions/0","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [endpoints] is not permitted"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has whitelistedApplicationIds" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidWhitelistedAppIdsInDefinition)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#/properties/api/properties/versions/items","pointerToViolation":"#/api/versions/0","causingExceptions":[{"schemaLocation":"#/properties/api/properties/versions/items/properties/access","pointerToViolation":"#/api/versions/0/access","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [whitelistedApplicationIds] is not permitted"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has PUBLISHED status" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidPublishedStatusInDefinition)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#/properties/api/properties/versions/items","pointerToViolation":"#/api/versions/0","causingExceptions":[{"schemaLocation":"#/properties/api/properties/versions/items/properties/status","pointerToViolation":"#/api/versions/0/status","causingExceptions":[{"pointerToViolation":"#/api/versions/0/status","causingExceptions":[],"keyword":"enum","message":"PUBLISHED is not a valid enum value"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has PROTOTYPED status" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidPrototypedStatusInDefinition)))

      val result         = await(connector.getProducerApiDefinition(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#/properties/api/properties/versions/items","pointerToViolation":"#/api/versions/0","causingExceptions":[{"schemaLocation":"#/properties/api/properties/versions/items/properties/status","pointerToViolation":"#/api/versions/0/status","causingExceptions":[{"pointerToViolation":"#/api/versions/0/status","causingExceptions":[],"keyword":"enum","message":"PROTOTYPED is not a valid enum value"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "Not fail if the API definition is invalid but it's configured to not do validation" in new SetupWithNoApiDefinitionValidation {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidContextInDefinition)))

      val result: Either[PublishError, ProducerApiDefinition] = await(connector.getProducerApiDefinition(testService))

      result.isRight shouldBe true
    }

    "should not parse nginx response to JSON" in new Setup {
      val badGatewayResponse =
        """<html>
          |<head><title>502 Bad Gateway</title></head>
          |<body bgcolor="white">
          |<center><h1>502 Bad Gateway</h1></center>
          |<hr><center>nginx</center>
          |</body>
          |</html>""".stripMargin

      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(BAD_GATEWAY).withBody(badGatewayResponse)))

      val badGatewayException = intercept[UpstreamErrorResponse] {
        await(connector.getProducerApiDefinition(testService))
      }

      badGatewayException.statusCode shouldBe BAD_GATEWAY
      badGatewayException.getMessage should include("<head><title>502 Bad Gateway</title></head>")
    }
  }

  "getOAS" should {
    "call the microservice to get the application.yaml" in new Setup {
      connector.getOAS(testService, "1.0")

      verify(mockOasFileLoader).load(eqTo(testService), eqTo("1.0"), *)
    }

  }
}
