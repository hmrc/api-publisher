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

package uk.gov.hmrc.apipublisher.controllers

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.apache.pekko.stream.Materializer
import org.mockito.BDDMockito.given
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.AsyncHmrcSpec

import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}

import uk.gov.hmrc.apipublisher.config.AppConfig
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.ApprovalStatus.NEW
import uk.gov.hmrc.apipublisher.models.PublisherApiStatus._
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.services._

class PublisherControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with StubControllerComponentsFactory {

  val serviceLocation              = ServiceLocation("test", "http://example.com", Some(Map("third-party-api" -> "true")))
  private val errorServiceLocation = ServiceLocation("ErrorService", "http://test.example.com")

  implicit val mat: Materializer = app.materializer

  private val sharedSecret = UUID.randomUUID().toString

  private val api                   = Json.parse(getClass.getResourceAsStream("/input/api-with-fields.json")).as[JsObject]
  private val producerApiDefinition = ProducerApiDefinition(api)

  private val employeeServiceApproval = APIApproval("employee-paye", "http://employeepaye.example.com", "Employee PAYE", Some("Test Description"), status = NEW)

  private val marriageAllowanceApproval =
    APIApproval("marriage-allowance", "http://marriage.example.com", "Marriage Allowance", Some("Check Marriage Allowance"), status = NEW)
  val serviceName                       = "employee-paye"
  val actor                             = Actors.GatekeeperUser("Dave Brown")
  val notes                             = Some("Good for approval")

  private val publisherResponse = PublisherResponse(
    name = "Example API",
    serviceName = "example-api",
    context = "test/example",
    description = "An example of an API",
    versions = List(PublisherApiVersion(
      version = "1.0",
      status = STABLE
    ))
  )

  trait BaseSetup {
    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockPublisherService       = mock[PublisherService]
    val mockApprovalService        = mock[ApprovalService]
    val mockAppConfig              = mock[AppConfig]
    val mockDefinitionService      = mock[DefinitionService]

    val underTest = new PublisherController(mockDefinitionService, mockPublisherService, mockApprovalService, mockAppConfig, stubControllerComponents())
  }

  trait Setup extends BaseSetup {
    when(mockDefinitionService.getDefinition(*)(*)).thenReturn(successful(Right(producerApiDefinition)))
    when(mockPublisherService.validation(eqTo(producerApiDefinition), eqTo(false))(*)).thenReturn(successful(None))
    when(mockPublisherService.publishAPIDefinition(eqTo(serviceLocation), *)(*)).thenReturn(successful(PublicationResult(
      approved = true,
      publisherResponse
    )))
    when(mockAppConfig.publishingKey).thenReturn(sharedSecret)

  }

  "publish" should {
    val validRequest = request(serviceLocation, sharedSecret)

    "respond with BAD_REQUEST when no definition is found" in new Setup {
      when(mockDefinitionService.getDefinition(eqTo(serviceLocation))(*)).thenReturn(successful(Left(DefinitionFileNotFound(serviceLocation))))

      val result = underTest.publish(validRequest)

      status(result) shouldEqual BAD_REQUEST
    }

    "respond with BAD_REQUEST with payload when validation returns an error" in new Setup {
      when(mockDefinitionService.getDefinition(*)(*)).thenReturn(successful(Right(producerApiDefinition)))
      when(mockPublisherService.validation(eqTo(producerApiDefinition), eqTo(false))(*)).thenReturn(successful(Some(JsString("Bang"))))

      val result = underTest.publish(validRequest)

      status(result) shouldEqual BAD_REQUEST
      contentAsString(result).contains("Bang") shouldBe true
    }

    "respond with 200 (OK) when service APIs successfully published" in new Setup {
      val validRequest = request(serviceLocation, sharedSecret)

      val result = underTest.publish(validRequest)

      status(result) shouldEqual OK
      contentAsJson(result) shouldBe Json.toJson(publisherResponse)
      verify(mockPublisherService).publishAPIDefinition(eqTo(serviceLocation), *)(*)
    }

    "respond with 202 (ACCEPTED) when service APIs not published because it awaits an approval" in new Setup {
      when(mockPublisherService.publishAPIDefinition(eqTo(serviceLocation), *)(*)).thenReturn(successful(PublicationResult(approved = false, publisherResponse)))

      val validRequest = request(serviceLocation, sharedSecret)

      val result = underTest.publish(validRequest)

      status(result) shouldEqual ACCEPTED
      contentAsJson(result) shouldBe Json.toJson(publisherResponse)
      verify(mockPublisherService).publishAPIDefinition(eqTo(serviceLocation), *)(*)
    }

    "return 500 (internal server error) when publisher service fails with an unexpected exception" in new Setup {
      val errorMessage         = "Test error"
      val expectedResponseBody = s"""{"code":"API_PUBLISHER_UNKNOWN_ERROR","message":"An unexpected error occurred: $errorMessage"}"""

      given(mockPublisherService.publishAPIDefinition(eqTo(errorServiceLocation), *)(*))
        .willReturn(Future.failed(new IllegalArgumentException(errorMessage)))

      val errorRequest = request(errorServiceLocation, sharedSecret)

      val result = underTest.publish(errorRequest)

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsString(result) shouldEqual expectedResponseBody
    }

    "return 401 (unauthorized) when Authorization header is not included in request" in new Setup {
      val requestWithoutAuthHeader = missingAuthHeaderRequest(serviceLocation)

      val result = underTest.publish(requestWithoutAuthHeader)

      status(result) shouldEqual UNAUTHORIZED
    }

    "return 401 (unauthorized) when Authorization header contains incorrect token" in new Setup {
      val requestWithBadToken = request(serviceLocation, "foo-bar-baz")

      val result = underTest.publish(requestWithBadToken)

      status(result) shouldEqual UNAUTHORIZED
    }

    "return 422 when publishing fails" in new Setup {
      when(mockPublisherService.publishAPIDefinition(eqTo(serviceLocation), *)(*))
        .thenReturn(Future.failed(new UnprocessableEntityException("")))

      val result = underTest.publish(request(serviceLocation, sharedSecret))

      status(result) shouldEqual UNPROCESSABLE_ENTITY
    }
  }

  "validate" should {

    "succeed when given a valid payload" in new Setup {

      when(mockPublisherService.validation(eqTo(producerApiDefinition), *)(*)).thenReturn(successful(None))

      val result = underTest.validate()(request(producerApiDefinition, sharedSecret))

      status(result) shouldEqual NO_CONTENT
    }

    "fail when given an invalid payload" in new Setup {

      val errorString = """{"error":"invalid-scope"}"""
      val input       = Json.parse(getClass.getResourceAsStream("/input/api-definition-invalid-scope.json"))
      when(mockPublisherService.validation(eqTo(input.as[ProducerApiDefinition]), *)(*))
        .thenReturn(successful(Some(Json.parse(errorString))))

      val result = underTest.validate()(FakeRequest().withHeaders(("Authorization", base64Encode(sharedSecret))).withBody(input))

      status(result) shouldEqual BAD_REQUEST
      contentAsString(result) shouldEqual errorString
    }

    "fail when an UnprocessableEntityException is thrown" in new Setup {

      val errorString = "Testing error"
      val input       = Json.parse(getClass.getResourceAsStream("/input/valid-api-definition.json"))
      when(mockPublisherService.validation(eqTo(input.as[ProducerApiDefinition]), *)(*))
        .thenReturn(Future.failed(new UnprocessableEntityException(errorString)))

      val result = underTest.validate()(FakeRequest().withHeaders(("Authorization", base64Encode(sharedSecret))).withBody(input))

      status(result) shouldEqual UNPROCESSABLE_ENTITY
      contentAsString(result) shouldEqual s"""{"code":"API_PUBLISHER_INVALID_REQUEST_PAYLOAD","message":"$errorString"}"""
    }

    "return 401 (unauthorized) when Authorization header is not included in request" in new Setup {
      val requestWithoutAuthHeader = missingAuthHeaderRequest(serviceLocation)

      val result = underTest.validate(requestWithoutAuthHeader)

      status(result) shouldEqual UNAUTHORIZED
    }

    "return 401 (unauthorized) when Authorization header contains incorrect token" in new Setup {
      val requestWithBadToken = request(serviceLocation, "foo-bar-baz")

      val result = underTest.validate(requestWithBadToken)

      status(result) shouldEqual UNAUTHORIZED
    }
  }

  "fetch all services" should {

    "retrieve a list of all services" in new Setup {

      when(mockApprovalService.fetchAllServices()).thenReturn(successful(List(employeeServiceApproval, marriageAllowanceApproval)))
      val result = underTest.fetchAllServices()(FakeRequest())

      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(Seq(employeeServiceApproval, marriageAllowanceApproval))
    }

    "retrieve an empty list when there are no services" in new Setup {
      when(mockApprovalService.fetchAllServices()).thenReturn(successful(List.empty))
      val result = underTest.fetchAllServices()(FakeRequest())

      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(Seq.empty[APIApproval])
    }
  }

  "search services" should {

    "return services" in new Setup {
      when(mockApprovalService.searchServices(*)).thenReturn(successful(List(employeeServiceApproval, marriageAllowanceApproval)))
      val result = underTest.searchServices()(FakeRequest("GET", "/services/search?status=NEW&status=APPROVED"))

      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(Seq(employeeServiceApproval, marriageAllowanceApproval))
      verify(mockApprovalService).searchServices(new ServicesSearch(List(New, Approved)))
    }

    "retrieve an empty list when there are no services" in new Setup {
      when(mockApprovalService.searchServices(*)).thenReturn(successful(List.empty))
      val result = underTest.searchServices()(FakeRequest())

      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(Seq.empty[APIApproval])
      verify(mockApprovalService).searchServices(new ServicesSearch(List.empty))
    }

    "return error if bad query string" in new Setup {
      val result = underTest.searchServices()(FakeRequest("GET", "/services/search?bla$%$%^%h"))

      status(result) shouldEqual BAD_REQUEST
    }
  }

  "fetch service summary" should {

    "retrieve the summary of a service when of a known service is requested" in new Setup {
      when(mockApprovalService.fetchServiceApproval("employee-paye")).thenReturn(successful(employeeServiceApproval))
      val result = underTest.fetchServiceSummary("employee-paye")(FakeRequest())

      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(employeeServiceApproval)
    }

    "generate an error when an unknown service is requested" in new Setup {
      when(mockApprovalService.fetchServiceApproval("unknown-service"))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))
      val result = underTest.fetchServiceSummary("unknown-service")(FakeRequest())

      status(result) shouldBe NOT_FOUND
    }
  }

  "approve service" should {
    val fakeRequest = FakeRequest("POST", s"/service/${serviceName}/approve")
      .withHeaders("content-type" -> "application/json")
      .withBody(Json.toJson(ApiApprovalRequest(serviceName, actor, notes)))

    "approve a known service" in new Setup {

      when(mockApprovalService.approveService(*, *, *)).thenReturn(successful(serviceLocation))
      when(mockPublisherService.publishAPIDefinition(eqTo(serviceLocation), *)(*)).thenReturn(successful(PublicationResult(
        approved = true,
        publisherResponse
      )))

      val result = underTest.approve(serviceName)(fakeRequest)

      status(result) shouldBe NO_CONTENT
      verify(mockApprovalService).approveService(serviceName, actor, notes)
      verify(mockPublisherService).publishAPIDefinition(eqTo(serviceLocation), *)(*)
    }

    "raise an error when attempting to approve an unknown service" in new Setup {
      when(mockApprovalService.approveService("unknown-service", actor, notes))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))

      val result = underTest.approve("unknown-service")(fakeRequest)

      status(result) shouldBe NOT_FOUND
      verify(mockPublisherService, never).publishAPIDefinition(any[ServiceLocation], *)(*)
    }

    "raise an error when attempting to approve without the correct body" in new Setup {
      when(mockApprovalService.approveService("unknown-service", actor, notes))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))

      val result = underTest.approve("unknown-service")(FakeRequest().withHeaders("content-type" -> "application/json"))

      status(result) shouldBe BAD_REQUEST
      verify(mockPublisherService, never).publishAPIDefinition(any[ServiceLocation], *)(*)
    }
  }

  "decline service" should {
    val fakeRequest = FakeRequest("POST", s"/service/${serviceName}/decline")
      .withHeaders("content-type" -> "application/json")
      .withBody(Json.toJson(ApiApprovalRequest(serviceName, actor, notes)))

    "decline a known service" in new Setup {

      when(mockApprovalService.declineService(*, *, *)).thenReturn(successful(serviceLocation))

      val result = underTest.decline(serviceName)(fakeRequest)

      status(result) shouldBe NO_CONTENT
      verify(mockApprovalService).declineService(serviceName, actor, notes)
    }

    "raise an error when attempting to decline an unknown service" in new Setup {
      when(mockApprovalService.declineService(*, *, *))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Decline Service. Unknown Service Name: unknown-service")))

      val result = underTest.decline("unknown-service")(fakeRequest)

      status(result) shouldBe NOT_FOUND
      verify(mockApprovalService).declineService("unknown-service", actor, notes)
    }
  }

  "add comment for an API approval" should {
    val fakeRequest = FakeRequest("POST", s"/service/${serviceName}/comment")
      .withHeaders("content-type" -> "application/json")
      .withBody(Json.toJson(ApiApprovalRequest(serviceName, actor, notes)))

    "add comment for a known service" in new Setup {

      when(mockApprovalService.addComment(*, *, *)).thenReturn(successful(serviceLocation))

      val result = underTest.addComment(serviceName)(fakeRequest)

      status(result) shouldBe NO_CONTENT
      verify(mockApprovalService).addComment(serviceName, actor, notes)
    }

    "raise an error when attempting to add a comment to an unknown service" in new Setup {
      when(mockApprovalService.addComment(*, *, *))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Add Comment. Unknown Service Name: unknown-service")))

      val result = underTest.addComment("unknown-service")(fakeRequest)

      status(result) shouldBe NOT_FOUND
      verify(mockApprovalService).addComment("unknown-service", actor, notes)
    }
  }

  def request[T](data: T, token: String)(implicit writes: Writes[T]): Request[JsValue] = {
    FakeRequest().withHeaders(("Authorization", base64Encode(token))).withBody(Json.toJson(data))
  }

  def missingAuthHeaderRequest[T](data: T)(implicit writes: Writes[T]): Request[JsValue] = {
    FakeRequest().withBody(Json.toJson(data))
  }

  def base64Encode(stringToEncode: String): String = {
    new String(Base64.getEncoder.encode(stringToEncode.getBytes), StandardCharsets.UTF_8)
  }

}
