/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.apipublisher.connectors.{APIDefinitionConnector, APIScopeConnector, APISubscriptionFieldsConnector}
import uk.gov.hmrc.apipublisher.models
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}


class PublisherServiceSpec extends AsyncHmrcSpec {

  val testServiceLocation = ServiceLocation("test", "http://example.com", Some(Map("third-party-api" -> "true")))

  val api = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints-and-fields.json")).as[JsObject]
  val scopes = Json.parse(getClass.getResourceAsStream("/input/scopes.json")).as[JsArray]
  val scopesSeq = Json.parse(getClass.getResourceAsStream("/input/scopes.json")).as[Seq[Scope]]
  val multiScopes = Json.parse(getClass.getResourceAsStream("/input/multi-scopes.json")).as[JsArray]
  val apiAndScopes = ApiAndScopes(api, scopes)
  val apiAndMultiScopes = ApiAndScopes(api, multiScopes)

  val apiWithoutFieldDefinitions = Json.parse(getClass.getResourceAsStream("/input/api-with-endpoints.json")).as[JsObject]
  val apiAndScopesWithoutFieldDefinitions = ApiAndScopes(apiWithoutFieldDefinitions, scopes)

  val apiContext = "test"
  val expectedApiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
    models.ApiFieldDefinitions(apiContext, "1.0",
      (Json.parse(getClass.getResourceAsStream("/input/field-definitions_1.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, "2.0",
      (Json.parse(getClass.getResourceAsStream("/input/field-definitions_2.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, "2.1",
      (Json.parse(getClass.getResourceAsStream("/input/field-definitions_2.1.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]))

  val expectedApiDocumentationRegistration = RegistrationRequest("test", "http://example.com", Seq("1.0", "2.0", "3.0"))

  val emulatedServiceError = new UnsupportedOperationException("Emulating a failure")

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockApiDefinitionConnector = mock[APIDefinitionConnector]
    val mockApiSubscriptionFieldsConnector = mock[APISubscriptionFieldsConnector]
    val mockApiScopeConnector = mock[APIScopeConnector]
    val mockApprovalService = mock[ApprovalService]

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

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation,apiAndScopes)) shouldBe true

      verify(mockApiDefinitionConnector).publishAPI(*)(*)
      verify(mockApiScopeConnector).publishScopes(eqTo(scopes))(*)
      verify(mockApiSubscriptionFieldsConnector).publishFieldDefinitions(eqTo(expectedApiFieldDefinitions))(*)
    }

    "Retrieve the api from the microservice but don't Publish it to api-definition, api-subscription-fields, api-scope and api-documentation if publication is not allowed" in new Setup {

      when(mockApprovalService.createOrUpdateServiceApproval(*)).thenReturn(successful(false))

      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation,apiAndScopes)) shouldBe false

      verifyZeroInteractions(mockApiDefinitionConnector)
      verifyZeroInteractions(mockApiScopeConnector)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "When publication allowed and api does not have subscription fields, publish API to api-definition, api-scope and api-documentation only" in new Setup {
      await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation,apiAndScopesWithoutFieldDefinitions)) shouldBe true

      verify(mockApiScopeConnector).publishScopes(eqTo(scopes))(*)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "Fail, propagating an error, when the apiScopeConnector fails" in new Setup {

      when(mockApiScopeConnector.publishScopes(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation,apiAndScopes))
      } shouldBe emulatedServiceError
    }

    "Fail, propagating an error, when the apiDefinitionConnector fails" in new Setup {

      when(mockApiDefinitionConnector.publishAPI(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinitionAndScopes(testServiceLocation,apiAndScopes))
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

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

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


      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeErrors":$errorString}"""
    }

    "Fail when api definition is invalid" in new Setup {

      val errorString = """{"error":"blah"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(Some(Json.parse( """{"error":"blah"}"""))))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"apiDefinitionErrors":$errorString}"""
    }

    "Fail when both api definition and scope are invalid" in new Setup {

      val scopeErrorString = """{"error":"invalid-scope"}"""
      val apiDefinitionErrorString = """{"error":"invalid-api-definition"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(Some(Json.parse(apiDefinitionErrorString))))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(Some(Json.parse(scopeErrorString))))
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(scopesSeq))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeErrors":$scopeErrorString,"apiDefinitionErrors":$apiDefinitionErrorString}"""
    }

    "Fail when scope updating is attempted on a pre-existing scope" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "I have changed"))
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Seq("read:hello")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))
      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeChangedErrors":"$scopeChangedErrorString"}"""
    }

    "Fail when adding a new scope is being attempted" in new Setup {
      val scopeFromScopeService = Seq()
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Seq("read:hello", "read:test")))(*)).thenReturn(successful(scopeFromScopeService))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeChangedErrors":"$scopeChangedErrorString"}"""
    }

    "Fail when requested for updating multiple scopes match with only one of many" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "I have changed"), Scope("read:test", "wrong test", "Make it fail"))
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Seq("read:hello", "read:test")))(*)).thenReturn(successful((scopeFromScopeService)))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"scopeChangedErrors":"$scopeChangedErrorString"}"""
    }

    "Validate successfully when defining multiple scopes which all match" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:hello", "Say Hello", "Ability to Say Hello"), Scope("read:test", "Test", "Another one to test"))

      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Seq("read:hello", "read:test")))(*)).thenReturn(successful((scopeFromScopeService)))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val errors = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      errors.isDefined shouldBe false
    }

    "Validate successfully ignoring order of scopes" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:test", "Test", "Another one to test"), Scope("read:hello", "Say Hello", "Ability to Say Hello"))

      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Seq("read:hello", "read:test")))(*)).thenReturn(successful((scopeFromScopeService)))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val errors = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      errors.isDefined shouldBe false
    }

    "Validate successfully when requested for updating multiple scopes match with all existing ones having out of order duplicates" in new Setup {
      val scopeFromScopeService = Seq(Scope("read:test", "Test", "Another one to test"), Scope("read:hello", "Say Hello", "Ability to Say Hello"),
        Scope("read:test", "Test", "Another one to test"))

      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.validateScopes(*)(*)).thenReturn(successful(None))
      when(mockApiScopeConnector.retrieveScopes(refEq(Seq("read:hello", "read:test")))(*)).thenReturn(successful((scopeFromScopeService)))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val errors = await(publisherService.validateAPIDefinitionAndScopes(apiAndMultiScopes))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiScopeConnector).validateScopes(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      errors.isDefined shouldBe false
    }
    "return error when the api definition references a scope that is undefined" in new Setup {
      when(mockApiScopeConnector.retrieveScopes(*)(*)).thenReturn(successful(Seq()))
      val input = Json.parse(getClass.getResourceAsStream("/input/api-definition-invalid-scope.json"))
      val errors = await(publisherService.validateAPIDefinitionAndScopes(input.as[ApiAndScopes]))
      assert(errors.isDefined)
      errors.get.toString shouldBe """{"scopeErrors":[{"field":"key","message":"Undefined scopes used in definition: [say:hello]"}]}"""
    }

  }

}
