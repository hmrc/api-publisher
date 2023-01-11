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

import java.io.FileNotFoundException
import java.{util => ju}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source

import akka.actor.ActorSystem
import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension
import io.swagger.v3.parser.core.models.{AuthorizationValue, ParseOptions, SwaggerParseResult}
import org.everit.json.schema.ValidationException
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.AsyncHmrcSpec

import play.api.libs.json.Json.parse
import play.api.libs.json.{JsArray, JsObject}
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

import uk.gov.hmrc.apipublisher.models.APICategory.{CUSTOMS, EXAMPLE, OTHER}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}

class MicroserviceConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {

  SharedMetricRegistries.clear()
  val apiProducerPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiProducerHost = "127.0.0.1"
  val apiProducerUrl  = s"http://$apiProducerHost:$apiProducerPort"
  val wireMockServer  = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiProducerPort))

  val testService = ServiceLocation("test.example.com", apiProducerUrl)

  val apiAndScopeDefinition                    = Source.fromURL(getClass.getResource("/input/api-definition-without-endpoints.json")).mkString
  val apiAndScopeDefinitionWithoutWhitelisting = Source.fromURL(getClass.getResource("/input/api-definition-without-endpoints-without-whitelistedAppIds.json")).mkString

  val invalidDefinition = Source.fromURL(getClass.getResource("/input/invalid-api-definition.json")).mkString

  val api                         = parse(getClass.getResourceAsStream("/input/api-without-endpoints.json")).as[JsObject]
  val apiWithoutWhitelistedAppIDs = parse(getClass.getResourceAsStream("/input/api-without-endpoints-without-whitelistedAppIDs.json")).as[JsObject]

  val scopes = parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]

  trait BaseSetup {
    WireMock.reset()
    val mockRamlLoader  = mock[DocumentationRamlLoader]
    implicit val hc     = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    implicit val system = app.injector.instanceOf[ActorSystem]

    def oasFileLocator: MicroserviceConnector.OASFileLocator
    def oasParser: SwaggerParserExtension

    val appConfig: Configuration = mock[Configuration]

    lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockRamlLoader,
      oasFileLocator,
      oasParser,
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Environment]
    )
  }

  trait Setup extends BaseSetup {
    val oasFileLocator = mock[MicroserviceConnector.OASFileLocator]
    val oasParser      = new OpenAPIV3Parser()
  }

  trait SetupWithTimedOutParser extends BaseSetup {
    val oasFileLocator = mock[MicroserviceConnector.OASFileLocator]

    val oasParser = new SwaggerParserExtension {

      override def readLocation(x$1: String, x$2: ju.List[AuthorizationValue], x$3: ParseOptions): SwaggerParseResult = {
        Thread.sleep(15000)
        throw new RuntimeException("Should have crashed out of the blocking by now")
      }

      override def readContents(x$1: String, x$2: ju.List[AuthorizationValue], x$3: ParseOptions): SwaggerParseResult = ???

    }
  }

  trait SetupWithNoApiDefinitionValidation extends Setup {

    override lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = false, oasParserMaxDuration = 3.seconds),
      mockRamlLoader,
      MicroserviceConnector.MicroserviceOASFileLocator,
      new OpenAPIV3Parser(),
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Environment]
    )
  }

  trait SetupWithMockedOpenApiParser extends Setup {
    val mockOpenApiParser = mock[OpenAPIV3Parser]

    override lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockRamlLoader,
      oasFileLocator,
      mockOpenApiParser,
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Environment]
    )
  }

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(apiProducerHost, apiProducerPort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  "getAPIDefinition" should {
    "Return the api definition" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinition)))

      val result = await(connector.getAPIAndScopes(testService))

      result shouldEqual Some(ApiAndScopes(api, scopes))
    }

    "Accept api definition for private API without whitelisted application IDs" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinitionWithoutWhitelisting)))

      val result = await(connector.getAPIAndScopes(testService))

      result shouldEqual Some(ApiAndScopes(apiWithoutWhitelistedAppIDs, scopes))
    }

    "Default categories to OTHER when API is not in categories map" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(apiAndScopeDefinition)))

      val result = await(connector.getAPIAndScopes(testService))

      result.get.categories should contain only OTHER
    }

    "Not default categories when API is in categories map but categories is defined in the definition" in new Setup {
      val helloDefinition = Source.fromURL(getClass.getResource("/input/hello-definition-with-categories.json")).mkString
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      val result = await(connector.getAPIAndScopes(testService))

      result.get.categories should contain only CUSTOMS
    }

    "Default categories when API is in categories map and categories is missing from the definition" in new Setup {
      val helloDefinition = Source.fromURL(getClass.getResource("/input/hello-definition-without-categories.json")).mkString
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      val result = await(connector.getAPIAndScopes(testService))

      result.get.categories should contain only EXAMPLE
    }

    "Default categories when API is in categories map and categories is empty from the definition" in new Setup {
      val helloDefinition = Source.fromURL(getClass.getResource("/input/hello-definition-with-empty-categories.json")).mkString
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(helloDefinition)))

      val result = await(connector.getAPIAndScopes(testService))

      result.get.categories should contain only EXAMPLE
    }

    "Return none if the API endpoint returns 204" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NO_CONTENT)))

      val result = await(connector.getAPIAndScopes(testService))

      result shouldEqual None
    }

    "Fail if the API endpoint returns 404" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NOT_FOUND)))

      intercept[UpstreamErrorResponse] {
        await(connector.getAPIAndScopes(testService))
      }.statusCode shouldBe NOT_FOUND
    }

    "Fail if the API definition is invalid" in new Setup {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinition)))

      intercept[ValidationException] {
        await(connector.getAPIAndScopes(testService))
      }
    }

    "Not fail if the API definition is invalid but it's configured to not do validation" in new SetupWithNoApiDefinitionValidation {
      stubFor(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinition)))

      val result: Option[ApiAndScopes] = await(connector.getAPIAndScopes(testService))

      result shouldBe defined
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
    "load the OAS file when found and is a valid model" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/application.yaml")

      await(connector.getOAS(testService, "1.0"))

      ok("Done")
    }

    "load the OAS file when multifile OAS is found and is a valid model" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/multifile/v1/application.yaml")

      await(connector.getOAS(testService, "1.0"))

      ok("Done")
    }

    "handle an invalid OAS file" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/bad-application.yaml")

      intercept[RuntimeException] {
        await(connector.getOAS(testService, "1.0"))
      }
    }

    "handle when the OAS file is not found" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/no-such-application.yaml")

      intercept[RuntimeException] {
        await(connector.getOAS(testService, "1.0"))
      }
    }

    "handle a FileNotFoundException when locating yaml specification" in new SetupWithMockedOpenApiParser {
      when(mockOpenApiParser.readLocation(*, *, *)).thenThrow(new FileNotFoundException("A problem reading the YAML file"))

      intercept[IllegalArgumentException] {
        await(connector.getOAS(testService, "1.0"))
      }.getMessage() shouldBe "Cannot find valid OAS file"
    }

    // Flakey test in build server...
    "return timeout when OAS parser takes too long" ignore new SetupWithTimedOutParser {
      import scala.concurrent.duration._

      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/no-such-application.yaml")

      intercept[IllegalStateException] {
        Await.result(connector.getOAS(testService, "1.0"), 29.seconds)
      }
    }
  }
}
