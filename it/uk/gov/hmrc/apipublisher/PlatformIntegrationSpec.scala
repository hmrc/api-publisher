package uk.gov.hmrc.apipublisher

import org.scalatest.TestData
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.{Application, Mode}
import uk.gov.hmrc.apipublisher.controllers.DocumentationController
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Testcase to verify the capability of integration with the API platform.
  * 1a, To expose API's to Third Party Developers, the service needs to make the API definition available under api/definition GET endpoint
  * 1b, The endpoints need to be defined in an application.raml file for all versions  For all of the endpoints defined documentation will be provided and be
  * available under api/documentation/[version]/[endpoint name] GET endpoint
  * Example: api/documentation/1.0/Fetch-Some-Data
  *
  * See: https://confluence.tools.tax.service.gov.uk/display/ApiPlatform/API+Platform+Architecture+with+Flows
  */
trait PlatformIntegrationSpec extends UnitSpec with GuiceOneAppPerTest {

  val publishApiDefinition: Boolean

  override def newAppForTest(testData: TestData): Application = GuiceApplicationBuilder()
    .configure("run.mode" -> "Stub")
    .configure(Map(
      "publishApiDefinition" -> publishApiDefinition,
      "api.context" -> "test-api-context"))
    .in(Mode.Test).build()

  trait Setup {
    implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

    val documentationController: DocumentationController = app.injector.instanceOf[DocumentationController]
    val request = FakeRequest()
  }
}

class PublishApiDefinitionEnabledSpec extends PlatformIntegrationSpec {
  val publishApiDefinition = true

  "microservice" should {
    "return the JSON definition" in new Setup {
      val result = await(documentationController.definition()(request))
      status(result) shouldBe OK
      bodyOf(result) should include(""""context": "test-api-context"""")
    }

    "return the RAML" in new Setup {
      val result = await(documentationController.raml("1.0", "application.raml")(request))
      status(result) shouldBe OK
      bodyOf(result) should include("/test-api-context")
    }
  }
}

class PublishApiDefinitionDisabledSpec extends PlatformIntegrationSpec {
  val publishApiDefinition = false

  "microservice" should {

    "return a 204 from the definition endpoint" in new Setup {
      val result = await(documentationController.definition()(request))
      status(result) shouldBe NO_CONTENT
    }

    "return a 204 from the RAML endpoint" in new Setup {
      val result = await(documentationController.raml("1.0", "application.raml")(request))
      status(result) shouldBe NO_CONTENT
    }
  }
}
