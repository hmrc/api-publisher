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

package uk.gov.hmrc.apipublisher.services

import org.mockito.Matchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{verify, verifyZeroInteractions}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import uk.gov.hmrc.apipublisher.connectors.{APIDefinitionConnector, APIScopeConnector, APISubscriptionFieldsConnector}
import uk.gov.hmrc.apipublisher.models
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class PublisherServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  val testServiceLocation = ServiceLocation("test", "http://example.com", Some(Map("third-party-api" -> "true")))

  val api = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints-and-fields.json")).as[JsObject]
  val scopes = Json.parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]
  val apiAndScopes = ApiAndScopes(api, scopes)

  val apiWithoutFieldDefinitions = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints.json")).as[JsObject]
  val apiAndScopesWithoutFieldDefinitions = ApiAndScopes(apiWithoutFieldDefinitions, scopes)

  val apiContext = "test"
  val expectedApiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
    models.ApiFieldDefinitions(apiContext, "1.0",
      (Json.parse(getClass.getResourceAsStream("/input/field-definitions_1.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, "2.0",
      (Json.parse(getClass.getResourceAsStream("/input/field-definitions_2.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]))

  val expectedApiDocumentationRegistration = RegistrationRequest("test", "http://example.com", Seq("1.0", "2.0", "3.0"))

  val emulatedServiceError = new UnsupportedOperationException("Emulating a failure")

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockDefinitionService = mock[DefinitionService]
    val mockApiDefinitionConnector = mock[APIDefinitionConnector]
    val mockApiSubscriptionFieldsConnector = mock[APISubscriptionFieldsConnector]
    val mockApiScopeConnector = mock[APIScopeConnector]
    val mockApprovalService = mock[ApprovalService]
    
    val publisherService = new PublisherService(mockDefinitionService,
      mockApiDefinitionConnector,
      mockApiSubscriptionFieldsConnector,
      mockApiScopeConnector,
      mockApprovalService)
    
    given(mockDefinitionService.getDefinition(testServiceLocation)).willReturn(Some(apiAndScopes))
    given(mockApprovalService.createOrUpdateServiceApproval(any[APIApproval])).willReturn(true)
    given(mockApiDefinitionConnector.publishAPI(any[JsObject])(any[HeaderCarrier])).willReturn(())
    given(mockApiSubscriptionFieldsConnector.publishFieldDefinitions(any[Seq[ApiFieldDefinitions]])(any[HeaderCarrier])).willReturn(())
    given(mockApiScopeConnector.publishScopes(any[JsValue])(any[HeaderCarrier])).willReturn(())
  }

  "publishAPIDefinitionAndScopes" should {

    "Return none and not do any publishing if the definition service returns none" in new Setup {
      given(mockDefinitionService.getDefinition(testServiceLocation)).willReturn(None)

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation)) shouldBe None

      verify(mockDefinitionService).getDefinition(testServiceLocation)
      verifyZeroInteractions(mockApiDefinitionConnector)
      verifyZeroInteractions(mockApiScopeConnector)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "Retrieve the api from the microservice and Publish it to api-definition, api-subscription-fields, api-scope and api-documentation if publication is allowed" in new Setup {

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation)) shouldBe Some(true)

      verify(mockDefinitionService).getDefinition(testServiceLocation)
      verify(mockApiDefinitionConnector).publishAPI(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).publishScopes(scopes)
      verify(mockApiSubscriptionFieldsConnector).publishFieldDefinitions(expectedApiFieldDefinitions)
    }

    "Retrieve the api from the microservice but don't Publish it to api-definition, api-subscription-fields, api-scope and api-documentation if publication is not allowed" in new Setup {

      given(mockApprovalService.createOrUpdateServiceApproval(any[APIApproval])).willReturn(false)

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation)) shouldBe Some(false)

      verify(mockDefinitionService).getDefinition(testServiceLocation)
      verifyZeroInteractions(mockApiDefinitionConnector)
      verifyZeroInteractions(mockApiScopeConnector)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "When publication allowed and api does not have subscription fields, publish API to api-definition, api-scope and api-documentation only" in new Setup {
      given(mockDefinitionService.getDefinition(testServiceLocation)).willReturn(Some(apiAndScopesWithoutFieldDefinitions))

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation)) shouldBe Some(true)

      verify(mockDefinitionService).getDefinition(testServiceLocation)
      verify(mockApiDefinitionConnector).publishAPI(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).publishScopes(scopes)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "Fail, propagating an error, when the publisherConnector fails" in new Setup {

      given(mockDefinitionService.getDefinition(testServiceLocation)).willReturn(failed(emulatedServiceError))

      val future = publisherService.publishAPIDefinitionAndScopes(testServiceLocation)

      whenReady(future.failed) { ex =>
        ex shouldBe emulatedServiceError
        verifyZeroInteractions(mockApiDefinitionConnector)
      }
    }

    "Fail, propagating an error, when the apiScopeConnector fails" in new Setup {

      given(mockApiScopeConnector.publishScopes(any[JsValue])(any[HeaderCarrier])).willReturn(failed(emulatedServiceError))

      val future = publisherService.publishAPIDefinitionAndScopes(testServiceLocation)

      whenReady(future.failed) { ex =>
        ex shouldBe emulatedServiceError
      }
    }

    "Fail, propagating an error, when the apiDefinitionConnector fails" in new Setup {

      given(mockApiDefinitionConnector.publishAPI(any[JsObject])(any[HeaderCarrier])).willReturn(failed(emulatedServiceError))

      val future = publisherService.publishAPIDefinitionAndScopes(testServiceLocation)

      whenReady(future.failed) { ex =>
        ex shouldBe emulatedServiceError
      }
    }

    "Fail, propagating an error, when the apiSubscriptionFieldsConnector fails" in new Setup {

      given(mockApiSubscriptionFieldsConnector.publishFieldDefinitions(any[Seq[ApiFieldDefinitions]])(any[HeaderCarrier]))
        .willReturn(failed(emulatedServiceError))

      val future = publisherService.publishAPIDefinitionAndScopes(testServiceLocation)

      whenReady(future.failed) { ex =>
        ex shouldBe emulatedServiceError
      }
    }
  }

  "validateAPIDefinitionAndScopes" should {

    "Succeed when no validation failures are detected" in new Setup {

      given(mockApiDefinitionConnector.validateAPIDefinition(any[JsObject])(any[HeaderCarrier])).willReturn(successful(None))
      given(mockApiScopeConnector.validateScopes(any[JsValue])(any[HeaderCarrier])).willReturn(successful(None))
      given(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(any())(any[HeaderCarrier])).willReturn(successful(None))

      await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).validateScopes(any[JsValue])(any[HeaderCarrier])
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(any())(any[HeaderCarrier])

    }

    "Fail when Field Definition is invalid" in new Setup {
      
      val errorString = """{"error":"blah"}"""
      given(mockApiDefinitionConnector.validateAPIDefinition(any[JsObject])(any[HeaderCarrier])).willReturn(successful(None))
      given(mockApiScopeConnector.validateScopes(any[JsValue])(any[HeaderCarrier])).willReturn(successful(None))
      given(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(any())(any[HeaderCarrier])).willReturn(successful(Some(Json.parse(errorString))))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).validateScopes(any[JsValue])(any[HeaderCarrier])
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(any())(any[HeaderCarrier])
      
      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"fieldDefinitionErrors":$errorString}""" 

    }

    "Fail when scope is invalid" in new Setup {

      val errorString = """{"error":"blah"}"""
      given(mockApiDefinitionConnector.validateAPIDefinition(any[JsObject])(any[HeaderCarrier])).willReturn(successful(None))
      given(mockApiScopeConnector.validateScopes(any[JsValue])(any[HeaderCarrier])).willReturn(successful(Some(Json.parse(errorString))))
      given(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(any())(any[HeaderCarrier])).willReturn(successful(None))


      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).validateScopes(any[JsValue])(any[HeaderCarrier])
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(any())(any[HeaderCarrier])


      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeErrors":$errorString}"""
    }

    "Fail when api definition is invalid" in new Setup {

      val errorString = """{"error":"blah"}"""
      given(mockApiDefinitionConnector.validateAPIDefinition(any[JsObject])(any[HeaderCarrier]))
        .willReturn(successful(Some(Json.parse( """{"error":"blah"}"""))))
      given(mockApiScopeConnector.validateScopes(any[JsValue])(any[HeaderCarrier])).willReturn(successful(None))
      given(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(any())(any[HeaderCarrier])).willReturn(successful(None))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).validateScopes(any[JsValue])(any[HeaderCarrier])
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(any())(any[HeaderCarrier])

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"apiDefinitionErrors":$errorString}"""
    }

    "Fail when both api definition and scope are invalid" in new Setup {

      val scopeErrorString = """{"error":"invalid-scope"}"""
      val apiDefinitionErrorString = """{"error":"invalid-api-definition"}"""
      given(mockApiDefinitionConnector.validateAPIDefinition(any[JsObject])(any[HeaderCarrier]))
        .willReturn(successful(Some(Json.parse(apiDefinitionErrorString))))
      given(mockApiScopeConnector.validateScopes(any[JsValue])(any[HeaderCarrier])).willReturn(successful(Some(Json.parse(scopeErrorString))))
      given(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(any())(any[HeaderCarrier])).willReturn(successful(None))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(any[JsObject])(any[HeaderCarrier])
      verify(mockApiScopeConnector).validateScopes(any[JsValue])(any[HeaderCarrier])
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(any())(any[HeaderCarrier])

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeErrors":$scopeErrorString,"apiDefinitionErrors":$apiDefinitionErrorString}"""
    }

    "Fail with UnprocessableEntityException when the api definition references a scope that is undefined" in new Setup {

      val input = Json.parse(getClass.getResourceAsStream("/input/api-definition-invalid-scope.json"))
      val errors = await(publisherService.validateAPIDefinitionAndScopes(input.as[ApiAndScopes]))
      assert(errors.isDefined)
      errors.get.toString shouldBe """{"scopeErrors":[{"field":"key","message":"Undefined scopes used in definition: [say:hello]"}]}"""
    }

  }

}
