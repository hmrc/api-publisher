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
import play.api.libs.json.{JsArray, JsObject, Json}
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

  val apiAndScopeDefinition                    = handleGetFileAndClose("/input/api-definition-without-endpoints.json")
  val apiAndScopeDefinitionWithoutWhitelisting = handleGetFileAndClose("/input/api-definition-without-endpoints-without-whitelistedAppIds.json")

  val invalidContextInDefinition           = handleGetFileAndClose("/input/invalid-context-in-api-definition.json")
  val invalidDefinitionWithScopeInEndpoint = handleGetFileAndClose("/input/api-definition-with-endpoints-and-scopes-defined.json")
  val invalidDefinitionWithEmptyScopes     = handleGetFileAndClose("/input/api-definition-with-endpoints-no-scopes-defined.json")
  val invalidDefinitionWithScopes          = handleGetFileAndClose("/input/invalid-api-definition-with-scopes.json")

  val api                         = parse(getClass.getResourceAsStream("/input/api-without-endpoints.json")).as[JsObject]
  val apiWithoutWhitelistedAppIDs = parse(getClass.getResourceAsStream("/input/api-without-endpoints-without-whitelistedAppIDs.json")).as[JsObject]

  val scopes = parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]

  trait Setup {
    WireMock.reset()
    val mockRamlLoader               = mock[DocumentationRamlLoader]
    implicit val hc: HeaderCarrier   = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    implicit val system: ActorSystem = app.injector.instanceOf[ActorSystem]

    val appConfig: Configuration = mock[Configuration]

    lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockRamlLoader,
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
      mockRamlLoader,
      mockOasFileLoader,
      app.injector.instanceOf[HttpClientV2],
      app.injector.instanceOf[Environment]
    )
  }

  trait SetupWithMockedOpenApiParser extends Setup {
    val mockOpenApiParser = mock[OpenAPIV3Parser]

    override lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockRamlLoader,
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
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinition)))

      await(connector.getAPIAndScopes(testService)).value shouldBe ApiAndScopes(api)
    }

    "Accept api definition for private API without whitelisted application IDs" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinitionWithoutWhitelisting)))

      await(connector.getAPIAndScopes(testService)).value shouldBe ApiAndScopes(apiWithoutWhitelistedAppIDs)
    }

    "Default categories to OTHER when API is not in categories map" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinition)))

      await(connector.getAPIAndScopes(testService)).value.categories should contain only OTHER
    }

    "Not default categories when API is in categories map but categories is defined in the definition" in new Setup {
      val helloDefinition = handleGetFileAndClose("/input/hello-definition-with-categories.json")
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      await(connector.getAPIAndScopes(testService)).value.categories should contain only CUSTOMS
    }

    "Default categories when API is in categories map and categories is missing from the definition" in new Setup {
      val helloDefinition = handleGetFileAndClose("/input/hello-definition-without-categories.json")
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      await(connector.getAPIAndScopes(testService)).value.categories should contain only EXAMPLE
    }

    "Default categories when API is in categories map and categories is empty from the definition" in new Setup {
      val helloDefinition = handleGetFileAndClose("/input/hello-definition-with-empty-categories.json")
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      await(connector.getAPIAndScopes(testService)).value.categories should contain only EXAMPLE
    }

    "Return DefinitionFileNoBodyReturned if the API endpoint returns 204" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NO_CONTENT)))

      await(connector.getAPIAndScopes(testService)) match {
        case Left(DefinitionFileNoBodyReturned(_)) => succeed
        case _                                     => fail()
      }

    }

    "return DefinitionFileNotFound if the API endpoint returns 404" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = await(connector.getAPIAndScopes(testService)).left.value
      result shouldBe DefinitionFileNotFound(
        ServiceLocation("test.example.com", "http://127.0.0.1:21112")
      )
    }

    "return DefinitionFileFailedSchemaValidation if context in API definition is invalid" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidContextInDefinition)))

      val result         = await(connector.getAPIAndScopes(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#","pointerToViolation":"#","causingExceptions":[{"schemaLocation":"#","pointerToViolation":"#","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [scopes] is not permitted"},{"schemaLocation":"#/properties/api/properties/context","pointerToViolation":"#/api/context","causingExceptions":[],"keyword":"pattern","message":"string [t] does not match pattern ^[a-z]+[a-z/\\-]{4,}$"}],"message":"2 schema violations found"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has scope in endpoint" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinitionWithScopeInEndpoint)))

      val result         = await(connector.getAPIAndScopes(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#/properties/api","pointerToViolation":"#/api","causingExceptions":[{"schemaLocation":"#/properties/api/properties/versions/items","pointerToViolation":"#/api/versions/0","causingExceptions":[{"schemaLocation":"#/properties/api/properties/versions/items/properties/access","pointerToViolation":"#/api/versions/0/access","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [whitelistApplicationIds] is not permitted"}],"keyword":"allOf","message":"#: only 1 subschema matches out of 2"},{"schemaLocation":"#/properties/api/properties/context","pointerToViolation":"#/api/context","causingExceptions":[],"keyword":"pattern","message":"string [test] does not match pattern ^[a-z]+[a-z/\\-]{4,}$"}],"message":"2 schema violations found"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has empty scopes" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinitionWithEmptyScopes)))

      val result         = await(connector.getAPIAndScopes(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#","pointerToViolation":"#","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [scopes] is not permitted"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "return DefinitionFileFailedSchemaValidation if the API definition has scopes" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinitionWithScopes)))

      val result         = await(connector.getAPIAndScopes(testService)).left.value
      val expectedErrors =
        Json.parse("""{"schemaLocation":"#","pointerToViolation":"#","causingExceptions":[],"keyword":"additionalProperties","message":"extraneous key [scopes] is not permitted"}""")
      result shouldBe DefinitionFileFailedSchemaValidation(expectedErrors)
    }

    "Not fail if the API definition is invalid but it's configured to not do validation" in new SetupWithNoApiDefinitionValidation {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidContextInDefinition)))

      val result: Either[PublishError, ApiAndScopes] = await(connector.getAPIAndScopes(testService))

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
        await(connector.getAPIAndScopes(testService))
      }

      badGatewayException.statusCode shouldBe BAD_GATEWAY
      badGatewayException.getMessage should include("<head><title>502 Bad Gateway</title></head>")
    }
  }

  "getRaml" should {
    "call the microservice to get the application.raml" in new Setup {
      connector.getRaml(testService, "1.0")

      verify(mockRamlLoader).load(testService.serviceUrl + "/api/conf/1.0/application.raml")
    }
  }

  "getOAS" should {
    "call the microservice to get the application.yaml" in new Setup {
      connector.getOAS(testService, "1.0")

      verify(mockOasFileLoader).load(eqTo(testService), eqTo("1.0"), *)
    }

  }
}
