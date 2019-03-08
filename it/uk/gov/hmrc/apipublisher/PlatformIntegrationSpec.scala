package uk.gov.hmrc.apipublisher

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, TestData}
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.{Application, Mode}
import uk.gov.hmrc.apipublisher.controllers.DocumentationController
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Testcase to verify the capability of integration with the API platform.
  *
  * 1, To integrate with API platform the service needs to register itself to the service locator by calling the /registration endpoint and providing
  * - application name
  * - application url
  *
  * 2a, To expose API's to Third Party Developers, the service needs to make the API definition available under api/definition GET endpoint
  * 2b, The endpoints need to be defined in an application.raml file for all versions  For all of the endpoints defined documentation will be provided and be
  * available under api/documentation/[version]/[endpoint name] GET endpoint
  * Example: api/documentation/1.0/Fetch-Some-Data
  *
  * See: https://confluence.tools.tax.service.gov.uk/display/ApiPlatform/API+Platform+Architecture+with+Flows
  */
class PlatformIntegrationSpec extends UnitSpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach with GuiceOneAppPerTest {

  val stubHost = "localhost"

  val stubPort = sys.env.getOrElse("WIREMOCK_SERVICE_LOCATOR_PORT", "21113").toInt

  val wireMockServer = new WireMockServer(wireMockConfig()
    .port(stubPort))

  override def newAppForTest(testData: TestData): Application = GuiceApplicationBuilder()
    .configure("run.mode" -> "Stub")
    .configure(Map(
      "appName" -> "application-name",
      "appUrl" -> "http://microservice-name.protected.mdtp",
      "publishApiDefinition" -> "true"))
    .in(Mode.Test).build()

  override def beforeEach(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
    stubFor(post(urlMatching("/subscription")).willReturn(aResponse().withStatus(NO_CONTENT)))
  }

  trait Setup {
    implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

    val documentationController = app.injector.instanceOf[DocumentationController]
    val request = FakeRequest()
  }

  "microservice" should {
    "provide definition endpoint and documentation endpoint for each api" in new Setup {
      def normalizeEndpointName(endpointName: String): String = endpointName.replaceAll(" ", "-")

      val result = documentationController.definition()(request)
      status(result) shouldBe OK
      bodyOf(result).futureValue should include("\"context\": \"api-publisher\"")
    }

    "provide raml documentation for v1.0" in new Setup {
      val result = documentationController.raml("1.0", "application.raml")(request)
      status(result) shouldBe OK
      bodyOf(result).futureValue should startWith("#%RAML 1.0")
    }
  }

  override protected def afterEach(): Unit = {
    wireMockServer.stop()
    wireMockServer.resetMappings()
  }
}
