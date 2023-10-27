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

package uk.gov.hmrc.apipublisher.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import utils.AsyncHmrcSpec

import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId

import uk.gov.hmrc.apipublisher.connectors.{APIDefinitionConnector, APIScopeConnector, APISubscriptionFieldsConnector}
import uk.gov.hmrc.apipublisher.models
import uk.gov.hmrc.apipublisher.models.PublisherApiStatus._
import uk.gov.hmrc.apipublisher.models._

class PublisherServiceSpec extends AsyncHmrcSpec {

  val testServiceLocation: ServiceLocation = ServiceLocation("test", "http://example.com", Some(Map("third-party-api" -> "true")))

  val api: JsObject                   = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints-and-fields.json")).as[JsObject]
  val apiWithTwoScopes: JsObject      = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints-with-two-scopes.json")).as[JsObject]
  val scopes: JsArray                 = Json.parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]
  val scopesSeq: Seq[Scope]           = Json.parse(getClass.getResourceAsStream("/input/scopes.json")).as[Seq[Scope]]
  val multiScopes: JsArray            = Json.parse(getClass.getResourceAsStream("/input/multi-scopes.json")).as[JsArray]
  val apiAndScopes: ApiAndScopes      = ApiAndScopes(api, scopes)
  val apiAndMultiScopes: ApiAndScopes = ApiAndScopes(api, multiScopes)

  val apiWithoutFieldDefinitions: JsObject              = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints.json")).as[JsObject]
  val apiAndScopesWithoutFieldDefinitions: ApiAndScopes = ApiAndScopes(apiWithoutFieldDefinitions, scopes)

  val apiContext = "test"

  val expectedApiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
    models.ApiFieldDefinitions(apiContext, "1.0", (Json.parse(getClass.getResourceAsStream("/input/field-definitions_1.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, "2.0", (Json.parse(getClass.getResourceAsStream("/input/field-definitions_2.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, "2.1", (Json.parse(getClass.getResourceAsStream("/input/field-definitions_2.1.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]])
  )

  val expectedApiDocumentationRegistration: RegistrationRequest = RegistrationRequest("test", "http://example.com", Seq("1.0", "2.0", "3.0"))

  val emulatedServiceError = new UnsupportedOperationException("Emulating a failure")

  val publisherResponse = PublisherResponse(
    name = "Test",
    serviceName = "test",
    context = "test",
    description = "Test API",
    versions = List(
      PublisherApiVersion(version = "1.0", status = STABLE, endpointsEnabled = None),
      PublisherApiVersion(version = "2.0", status = STABLE, endpointsEnabled = None),
      PublisherApiVersion(version = "2.1", status = STABLE, endpointsEnabled = None),
      PublisherApiVersion(version = "3.0", status = BETA, endpointsEnabled = None)
    )
  )

  trait Setup {
    implicit val hc: HeaderCarrier                                         = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockApiDefinitionConnector: APIDefinitionConnector                 = mock[APIDefinitionConnector]
    val mockApiSubscriptionFieldsConnector: APISubscriptionFieldsConnector = mock[APISubscriptionFieldsConnector]
    val mockApiScopeConnector: APIScopeConnector                           = mock[APIScopeConnector]
    val mockApprovalService: ApprovalService                               = mock[ApprovalService]

    val publisherService = new PublisherService(
      mockApiDefinitionConnector,
      mockApiSubscriptionFieldsConnector,
      mockApiScopeConnector,
      mockApprovalService
    )

    when(mockApprovalService.createOrUpdateServiceApproval(*)).thenReturn(successful(true))
    when(mockApiDefinitionConnector.publishAPI(*)(*)).thenReturn(successful(()))
    when(mockApiSubscriptionFieldsConnector.publishFieldDefinitions(*)(*)).thenReturn(successful(()))
    when(mockApiScopeConnector.publishScopes(*)(*)).thenReturn(successful(()))
  }

  "publishAPIDefinitionAndScopes" should {

    "Retrieve the api from the microservice and Publish it to api-definition, api-subscription-fields, api-scope and api-documentation if publication is allowed" in new Setup {

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation, apiAndScopes)) shouldBe PublicationResult(approved = true, Some(publisherResponse))

      verify(mockApiDefinitionConnector).publishAPI(*)(*)
      verify(mockApiScopeConnector).publishScopes(eqTo(scopes))(*)
      verify(mockApiSubscriptionFieldsConnector).publishFieldDefinitions(eqTo(expectedApiFieldDefinitions))(*)
    }

    "Retrieve the api from the microservice but don't Publish it to api-definition, api-subscription-fields, api-scope and api-documentation if publication is not allowed" in new Setup {

      when(mockApprovalService.createOrUpdateServiceApproval(*)).thenReturn(successful(false))

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation, apiAndScopes)) shouldBe PublicationResult(approved = false, None)

      verifyZeroInteractions(mockApiDefinitionConnector)
      verifyZeroInteractions(mockApiScopeConnector)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "When publication allowed and api does not have subscription fields, publish API to api-definition, api-scope and api-documentation only" in new Setup {
      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation, apiAndScopesWithoutFieldDefinitions)) shouldBe PublicationResult(approved = true, Some(publisherResponse))

      verify(mockApiScopeConnector).publishScopes(eqTo(scopes))(*)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "Fail, propagating an error, when the apiScopeConnector fails" in new Setup {

      when(mockApiScopeConnector.publishScopes(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation, apiAndScopes))
      } shouldBe emulatedServiceError
    }

    "Fail, propagating an error, when the apiDefinitionConnector fails" in new Setup {

      when(mockApiDefinitionConnector.publishAPI(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation, apiAndScopes))
      } shouldBe emulatedServiceError
    }

    "Fail, propagating an error, when the apiSubscriptionFieldsConnector fails" in new Setup {

      when(mockApiSubscriptionFieldsConnector.publishFieldDefinitions(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation, apiAndScopes))
      } shouldBe emulatedServiceError
    }
  }

  "validateAPIDefinitionAndScopes" should {
    val scopeChangedErrorString =
      "Updating scopes while publishing is no longer supported. " +
        "See https://confluence.tools.tax.service.gov.uk/display/TEC/2021/09/07/Changes+to+scopes for more information"

    "Succeed when no validation failures are detected" in new Setup {
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

    }

    "Fail when Field Definition is invalid" in new Setup {

      val errorString = """{"error":"blah"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(Some(Json.parse(errorString))))

      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"fieldDefinitionErrors":$errorString}"""
    }

    "Fail when scope is invalid" in new Setup {

      val errorString = """{"error":"blah"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(Some(Json.parse(errorString))))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeErrors":$errorString}"""
    }

    "Fail when api definition is invalid" in new Setup {
      val errorString = """{"error":"blah"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(Some(Json.parse("""{"error":"blah"}"""))))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"apiDefinitionErrors":$errorString}"""
    }

    "Fail when both api definition and scope are invalid" in new Setup {
      val scopeErrorString         = """{"error":"invalid-scope"}"""
      val apiDefinitionErrorString = """{"error":"invalid-api-definition"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(Some(Json.parse(apiDefinitionErrorString))))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(Some(Json.parse(scopeErrorString))))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeErrors":$scopeErrorString,"apiDefinitionErrors":$apiDefinitionErrorString}"""
    }

    "Fail when referencing a scope not in scope database and not in API definition" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "I have changed"))
      when(mockApiScopeConnector.retrieveScopes(refEq(Set("read:hello", "read:test")))(*)).thenReturn(successful(scopeFromScopeService))

      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiScopeConnector).retrieveScopes(refEq(Set("read:hello", "read:test")))(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe """{"scopeErrors":[{"field":"key","message":"Undefined scopes used in definition: [read:test]"}]}"""
    }

    "Fail when attempting to update an existing scope" in new Setup {
      val scopeFromScopeService   = Seq(Scope("read:hello", "Say Hello", "I have changed"))
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Set("read:hello")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))
      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeChangedErrors":"$scopeChangedErrorString"}"""
    }

    "Fail when two scopes have different definitions to the scope database" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "I have changed"), Scope("read:test", "wrong test", "Make it fail"))
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Set("read:hello", "read:test")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeChangedErrors":"$scopeChangedErrorString"}"""
    }

    "Succeed when defining multiple scopes which all match how they are defined in the scopes database" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "Ability to Say Hello"), Scope("read:test", "Test", "Another one to test"))

      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Set("read:hello", "read:test")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val errors: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      errors.isDefined shouldBe false
    }

    "Succeed when API defines no scopes but uses one which is in the scopes database" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "Ability to Say Hello"))

      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Set("read:hello")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val errors: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(ApiAndScopes(api, JsArray())))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      errors.isDefined shouldBe false
    }

    "Succeed when API defines one scope and references one from the scopes database" in new Setup {
      val scopeFromScopeService  = Seq(Scope("read:hello", "Say Hello", "Ability to Say Hello"), Scope("read:goodbye", "Say Goodbye", "Ability to Say Goodbye"))
      val scopeFromApiDefinition = """{"key": "read:goodbye", "name": "Say Goodbye", "description": "Ability to Say Goodbye"}"""

      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Set("read:goodbye", "read:hello")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val errors: Option[JsValue] = await(publisherService.validateAPIDefinitionAndScopes(ApiAndScopes(apiWithTwoScopes, JsArray(Seq(Json.parse(scopeFromApiDefinition))))))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      errors.isDefined shouldBe false
    }
  }
}
