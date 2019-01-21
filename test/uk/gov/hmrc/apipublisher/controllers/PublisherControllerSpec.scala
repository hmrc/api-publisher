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

package uk.gov.hmrc.apipublisher.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{APIApproval, ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.apipublisher.services.{ApprovalService, PublisherService}
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class PublisherControllerSpec extends UnitSpec with MockitoSugar with OneAppPerSuite {

  private val serviceLocation = ServiceLocation("TestService", "http://test.example.com")
  private val errorServiceLocation = ServiceLocation("ErrorService", "http://test.example.com")

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockPublisherService = mock[PublisherService]
    val mockApprovalService = mock[ApprovalService]

    val underTest = new PublisherController(mockPublisherService, mockApprovalService)

    when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])).thenReturn(Future.successful(true))

    val employeeServiceApproval = APIApproval("employee-paye", "http://employeepaye.example.com", "Employee PAYE", Some("Test Description"), Some(false))
    val marriageAllowanceApproval = APIApproval("marriage-allowance", "http://marriage.example.com", "Marriage Allowance", Some("Check Marriage Allowance"), Some(false))
  }

  "publish" should {

    "respond with 204 (NO_CONTENT) when service APIs successfully published" in new Setup {
      val validRequest = request(serviceLocation)

      val result = await(underTest.publish(validRequest))

      status(result) shouldEqual NO_CONTENT
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])
    }

    "respond with 202 (ACCEPTED) when service APIs not published because it awaits an approval" in new Setup {
      when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])).thenReturn(Future.successful(false))

      val validRequest = request(serviceLocation)

      val result = await(underTest.publish(validRequest))

      status(result) shouldEqual ACCEPTED
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(serviceLocation))(any[HeaderCarrier])
    }

    "return 500 (internal server error) when publisher service fails with an unexpected exception" in new Setup {

      given(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(errorServiceLocation))(any[HeaderCarrier])).willReturn(Future.failed(new IllegalArgumentException("Test error")))

      val errorRequest = request(errorServiceLocation)

      val result: Result = await(underTest.publish(errorRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }

  "validate" should {

    "succeed when given a valid payload" in new Setup {

      val api = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints-and-fields.json")).as[JsObject]
      val scopes = Json.parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]
      val apiAndScopes = ApiAndScopes(api, scopes)
      when(mockPublisherService.validateAPIDefinitionAndScopes(ArgumentMatchers.eq(apiAndScopes))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      val result = await(underTest.validate()(request(apiAndScopes)))

      status(result) shouldEqual NO_CONTENT
    }

    "fail when given an invalid payload" in new Setup {

      val errorString = """{"error":"invalid-scope"}"""
      val input = Json.parse(getClass.getResourceAsStream("/input/api-definition-invalid-scope.json"))
      when(mockPublisherService.validateAPIDefinitionAndScopes(ArgumentMatchers.eq(input.as[ApiAndScopes]))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Json.parse(errorString))))

      val result = await(underTest.validate()(FakeRequest().withBody(input)))

      status(result) shouldEqual BAD_REQUEST
      bodyOf(result) shouldEqual errorString
    }

    "fail when an UnprocessableEntityException is thrown" in new Setup {

      val errorString = "TESTING! Scope blah has not been defined"
      val input = Json.parse(getClass.getResourceAsStream("/input/api-definition-with-endpoints.json"))
      when(mockPublisherService.validateAPIDefinitionAndScopes(ArgumentMatchers.eq(input.as[ApiAndScopes]))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new UnprocessableEntityException(errorString)))

      val result = await(underTest.validate()(FakeRequest().withBody(input)))

      status(result) shouldEqual UNPROCESSABLE_ENTITY
      bodyOf(result) shouldEqual s"""{"code":"API_PUBLISHER_INVALID_REQUEST_PAYLOAD","message":"$errorString"}"""
    }

  }

  "fetch unapproved services" should {

    "retrieve a list of unapproved services" in new Setup {

      when(mockApprovalService.fetchUnapprovedServices()).thenReturn(Future.successful(Seq(employeeServiceApproval, marriageAllowanceApproval)))
      val result = await(underTest.fetchUnapprovedServices()(FakeRequest()))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual Json.toJson(Seq(employeeServiceApproval, marriageAllowanceApproval))
    }

    "retrieve an empty list when there are no unapproved services" in new Setup {
      when(mockApprovalService.fetchUnapprovedServices()).thenReturn(Future.successful(Seq.empty))
      val result = await(underTest.fetchUnapprovedServices()(FakeRequest()))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual Json.toJson(Seq.empty[APIApproval])
    }
  }

  "fetch service summary" should {

    "retrieve the summary of a service when of a known service is requested" in new Setup {
      when(mockApprovalService.fetchServiceApproval("employee-paye")).thenReturn(Future.successful(employeeServiceApproval))
      val result = await(underTest.fetchServiceSummary("employee-paye")(FakeRequest()))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual Json.toJson(employeeServiceApproval)
    }

    "generate an error when an unknown service is requested" in new Setup {
      when(mockApprovalService.fetchServiceApproval("unknown-service")).thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))
      val result = await(underTest.fetchServiceSummary("unknown-service")(FakeRequest()))

      status(result) shouldBe NOT_FOUND
    }
  }

  "approve service" should {

    "approve a known service" in new Setup {

      private val testServiceLocation = ServiceLocation("employee-paye", "http://localhost/employee-paye")
      when(mockApprovalService.approveService("employee-paye")).thenReturn(Future.successful(testServiceLocation))
      when(mockPublisherService.publishAPIDefinitionAndScopes(ArgumentMatchers.eq(testServiceLocation))(any[HeaderCarrier])).thenReturn(Future.successful(true))

      val result = await(underTest.approve("employee-paye")(FakeRequest()))

      status(result) shouldBe NO_CONTENT
      verify(mockPublisherService).publishAPIDefinitionAndScopes(ArgumentMatchers.eq(testServiceLocation))(any[HeaderCarrier])
    }

    "raise an error when attempting to approve an unknown service" in new Setup {

      when(mockApprovalService.approveService("unknown-service")).thenReturn(Future.failed(UnknownApiServiceException(s"Unable to Approve Service. Unknown Service Name: unknown-service")))

      val result = await(underTest.approve("unknown-service")(FakeRequest()))

      status(result) shouldBe NOT_FOUND
      verify(mockPublisherService, never()).publishAPIDefinitionAndScopes(any[ServiceLocation])(any[HeaderCarrier])
    }

  }

  def request[T](data: T)(implicit writes: Writes[T]): Request[JsValue] = {
    FakeRequest().withBody(Json.toJson(data))
  }
}
