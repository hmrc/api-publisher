/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.Materializer
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{APIApproval, ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.apipublisher.services.{ApprovalService, PublisherService}
import uk.gov.hmrc.apipublisher.wiring.AppContext
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class PublisherControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  private val serviceLocation = ServiceLocation("TestService", "http://test.example.com")
  private val errorServiceLocation = ServiceLocation("ErrorService", "http://test.example.com")

  implicit val mat: Materializer = app.materializer

  private val sharedSecret = UUID.randomUUID().toString

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockPublisherService = mock[PublisherService]
    val mockApprovalService = mock[ApprovalService]
    val mockAppContext = mock[AppContext]

    val underTest = new PublisherController(mockPublisherService, mockApprovalService, mockAppContext)

    when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])).thenReturn(successful(Some(true)))
    when(mockAppContext.publishingKey).thenReturn(sharedSecret)

    val employeeServiceApproval = APIApproval("employee-paye", "http://employeepaye.example.com", "Employee PAYE", Some("Test Description"), Some(false))
    val marriageAllowanceApproval =
      APIApproval("marriage-allowance", "http://marriage.example.com", "Marriage Allowance", Some("Check Marriage Allowance"), Some(false))
  }

  "publish" should {

    "respond with 204 (NO_CONTENT) when service APIs successfully published" in new Setup {
      val validRequest = request(serviceLocation, sharedSecret)

      val result = await(underTest.publish(validRequest))

      status(result) shouldEqual NO_CONTENT
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])
    }

    "respond with 204 (NO_CONTENT) when publisher service returns none" in new Setup {
      when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])).thenReturn(successful(None))
      val validRequest = request(serviceLocation, sharedSecret)

      val result = await(underTest.publish(validRequest))

      status(result) shouldEqual NO_CONTENT
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])
    }

    "respond with 202 (ACCEPTED) when service APIs not published because it awaits an approval" in new Setup {
      when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])).thenReturn(successful(Some(false)))

      val validRequest = request(serviceLocation, sharedSecret)

      val result = await(underTest.publish(validRequest))

      status(result) shouldEqual ACCEPTED
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])
    }

    "return 500 (internal server error) when publisher service fails with an unexpected exception" in new Setup {
      val errorMessage = "Test error"
      val expectedResponseBody = s"""{"code":"API_PUBLISHER_UNKNOWN_ERROR","message":"An unexpected error occurred: $errorMessage"}"""

      given(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(errorServiceLocation))(any[HeaderCarrier]))
        .willReturn(Future.failed(new IllegalArgumentException(errorMessage)))

      val errorRequest = request(errorServiceLocation, sharedSecret)

      val result: Result = await(underTest.publish(errorRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      bodyOf(result) shouldEqual expectedResponseBody
    }

    "return 401 (unauthorized) when Authorization header is not included in request" in new Setup {
      val requestWithoutAuthHeader = missingAuthHeaderRequest(serviceLocation)

      val result: Result = await(underTest.publish(requestWithoutAuthHeader))

      status(result) shouldEqual UNAUTHORIZED
    }

    "return 401 (unauthorized) when Authorization header contains incorrect token" in new Setup {
      val requestWithBadToken = request(serviceLocation, "foo-bar-baz")

      val result: Result = await(underTest.publish(requestWithBadToken))

      status(result) shouldEqual UNAUTHORIZED
    }

    "return 422 when publishing fails" in new Setup {
      when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new UnprocessableEntityException("")))

      val result = await(underTest.publish(request(serviceLocation, sharedSecret)))

      status(result) shouldEqual UNPROCESSABLE_ENTITY
    }
  }

  "validate" should {

    "succeed when given a valid payload" in new Setup {

      val api = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints-and-fields.json"))
      val scopes = Json.parse(getClass.getResourceAsStream("/input/scopes.json"))
      val apiAndScopes = ApiAndScopes(api.as[JsObject], scopes.as[JsArray])
      when(mockPublisherService.validateAPIDefinitionAndScopes(ArgumentMatchers.eq(apiAndScopes))(any[HeaderCarrier])).thenReturn(successful(None))

      val result = await(underTest.validate()(request(apiAndScopes, sharedSecret)))

      status(result) shouldEqual NO_CONTENT
    }

    "fail when given an invalid payload" in new Setup {

      val errorString = """{"error":"invalid-scope"}"""
      val input = Json.parse(getClass.getResourceAsStream("/input/api-definition-invalid-scope.json"))
      when(mockPublisherService.validateAPIDefinitionAndScopes(ArgumentMatchers.eq(input.as[ApiAndScopes]))(any[HeaderCarrier]))
        .thenReturn(successful(Some(Json.parse(errorString))))

      val result = await(underTest.validate()(FakeRequest().withHeaders(("Authorization", base64Encode(sharedSecret))).withBody(input)))

      status(result) shouldEqual BAD_REQUEST
      bodyOf(result) shouldEqual errorString
    }

    "fail when an UnprocessableEntityException is thrown" in new Setup {

      val errorString = "TESTING! Scope blah has not been defined"
      val input = Json.parse(getClass.getResourceAsStream("/input/api-definition-with-endpoints.json"))
      when(mockPublisherService.validateAPIDefinitionAndScopes(ArgumentMatchers.eq(input.as[ApiAndScopes]))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new UnprocessableEntityException(errorString)))

      val result = await(underTest.validate()(FakeRequest().withHeaders(("Authorization", base64Encode(sharedSecret))).withBody(input)))

      status(result) shouldEqual UNPROCESSABLE_ENTITY
      bodyOf(result) shouldEqual s"""{"code":"API_PUBLISHER_INVALID_REQUEST_PAYLOAD","message":"$errorString"}"""
    }

    "return 401 (unauthorized) when Authorization header is not included in request" in new Setup {
      val requestWithoutAuthHeader = missingAuthHeaderRequest(serviceLocation)

      val result: Result = await(underTest.validate(requestWithoutAuthHeader))

      status(result) shouldEqual UNAUTHORIZED
    }

    "return 401 (unauthorized) when Authorization header contains incorrect token" in new Setup {
      val requestWithBadToken = request(serviceLocation, "foo-bar-baz")

      val result: Result = await(underTest.validate(requestWithBadToken))

      status(result) shouldEqual UNAUTHORIZED
    }
  }

  "fetch unapproved services" should {

    "retrieve a list of unapproved services" in new Setup {

      when(mockApprovalService.fetchUnapprovedServices()).thenReturn(successful(Seq(employeeServiceApproval, marriageAllowanceApproval)))
      val result = await(underTest.fetchUnapprovedServices()(FakeRequest()))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual Json.toJson(Seq(employeeServiceApproval, marriageAllowanceApproval))
    }

    "retrieve an empty list when there are no unapproved services" in new Setup {
      when(mockApprovalService.fetchUnapprovedServices()).thenReturn(successful(Seq.empty))
      val result = await(underTest.fetchUnapprovedServices()(FakeRequest()))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual Json.toJson(Seq.empty[APIApproval])
    }
  }

  "fetch service summary" should {

    "retrieve the summary of a service when of a known service is requested" in new Setup {
      when(mockApprovalService.fetchServiceApproval("employee-paye")).thenReturn(successful(employeeServiceApproval))
      val result = await(underTest.fetchServiceSummary("employee-paye")(FakeRequest()))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual Json.toJson(employeeServiceApproval)
    }

    "generate an error when an unknown service is requested" in new Setup {
      when(mockApprovalService.fetchServiceApproval("unknown-service"))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))
      val result = await(underTest.fetchServiceSummary("unknown-service")(FakeRequest()))

      status(result) shouldBe NOT_FOUND
    }
  }

  "approve service" should {

    "approve a known service" in new Setup {

      private val testServiceLocation = ServiceLocation("employee-paye", "http://localhost/employee-paye")
      when(mockApprovalService.approveService("employee-paye")).thenReturn(successful(testServiceLocation))
      when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(testServiceLocation))(any[HeaderCarrier])).thenReturn(successful(Some(true)))

      val result = await(underTest.approve("employee-paye")(FakeRequest()))

      status(result) shouldBe NO_CONTENT
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(testServiceLocation))(any[HeaderCarrier])
    }

    "raise an error when attempting to approve an unknown service" in new Setup {

      when(mockApprovalService.approveService("unknown-service"))
        .thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))

      val result = await(underTest.approve("unknown-service")(FakeRequest()))

      status(result) shouldBe NOT_FOUND
      verify(mockPublisherService, never()).publishAPIDefinitionAndScopes(any[ServiceLocation])(any[HeaderCarrier])
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
