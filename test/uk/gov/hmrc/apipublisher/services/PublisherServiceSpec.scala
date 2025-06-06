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

import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId

import uk.gov.hmrc.apipublisher.connectors._
import uk.gov.hmrc.apipublisher.models
import uk.gov.hmrc.apipublisher.models.PublisherApiStatus._
import uk.gov.hmrc.apipublisher.models._

class PublisherServiceSpec extends AsyncHmrcSpec with ApplicationWithCollaboratorsFixtures with FixedClock {

  val testServiceLocation: ServiceLocation = ServiceLocation("test", "http://example.com", Some(Map("third-party-api" -> "true")))

  val api: JsObject                                = Json.parse(getClass.getResourceAsStream("/input/api-with-fields.json")).as[JsObject]
  val producerApiDefinition: ProducerApiDefinition = ProducerApiDefinition(api)

  val apiWithoutFieldDefinitions: JsObject                                = Json.parse(getClass.getResourceAsStream("/input/valid-api.json")).as[JsObject]
  val producerApiDefinitionWithoutFieldDefinitions: ProducerApiDefinition = ProducerApiDefinition(apiWithoutFieldDefinitions)

  val apiContext = "test/api"

  val expectedApiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
    models.ApiFieldDefinitions(apiContext, "1.0", (Json.parse(getClass.getResourceAsStream("/input/field-definitions_1.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]]),
    models.ApiFieldDefinitions(apiContext, "2.0", (Json.parse(getClass.getResourceAsStream("/input/field-definitions_2.json")) \ "fieldDefinitions").as[Seq[FieldDefinition]])
  )

  val expectedApiDocumentationRegistration: RegistrationRequest = RegistrationRequest("test", "http://example.com", Seq("1.0", "2.0", "3.0"))

  val emulatedServiceError = new UnsupportedOperationException("Emulating a failure")

  val publisherResponse = PublisherResponse(
    name = "Test",
    serviceName = "test",
    context = "test/api",
    description = "Test API",
    versions = List(
      PublisherApiVersion(version = "1.0", status = STABLE),
      PublisherApiVersion(version = "2.0", status = STABLE),
      PublisherApiVersion(version = "3.0", status = BETA)
    )
  )

  trait Setup {
    implicit val hc: HeaderCarrier                                         = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    val mockApiDefinitionConnector: APIDefinitionConnector                 = mock[APIDefinitionConnector]
    val mockApiSubscriptionFieldsConnector: APISubscriptionFieldsConnector = mock[APISubscriptionFieldsConnector]
    val mockTpaConnector: TpaConnector                                     = mock[TpaConnector]
    val mockApprovalService: ApprovalService                               = mock[ApprovalService]

    val publisherService = new PublisherService(
      mockApiDefinitionConnector,
      mockApiSubscriptionFieldsConnector,
      mockTpaConnector,
      mockApprovalService,
      clock
    )

    when(mockApprovalService.createOrUpdateServiceApproval(*)).thenReturn(successful(true))
    when(mockApiDefinitionConnector.publishAPI(*)(*)).thenReturn(successful(()))
    when(mockApiSubscriptionFieldsConnector.publishFieldDefinitions(*)(*)).thenReturn(successful(()))

    val apiWith2RetiredVersions: JsObject                                = Json.parse(getClass.getResourceAsStream("/input/api-with-2-retired-status.json")).as[JsObject]
    val producerApiDefinitionWith2RetiredVersions: ProducerApiDefinition = ProducerApiDefinition(apiWith2RetiredVersions)
  }

  "publishAPIDefinition" should {

    "Retrieve the api from the microservice and Publish it to api-definition, api-subscription-fields and api-documentation if publication is allowed" in new Setup {

      await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinition)) shouldBe PublicationResult(approved = true, publisherResponse)

      verify(mockApiDefinitionConnector).publishAPI(*)(*)
      verify(mockApiSubscriptionFieldsConnector).publishFieldDefinitions(eqTo(expectedApiFieldDefinitions))(*)
    }

    "Publish is successful with 1 Retired version" in new Setup {

      val retiredApi: JsObject                                = Json.parse(getClass.getResourceAsStream("/input/api-with-retired-status.json")).as[JsObject]
      val retiredProducerApiDefinition: ProducerApiDefinition = ProducerApiDefinition(retiredApi)

      val retiredPublisherResponse = PublisherResponse(
        name = "Test",
        serviceName = "test",
        context = "test",
        description = "Test API",
        versions = List(
          PublisherApiVersion(version = "1.0", status = RETIRED),
          PublisherApiVersion(version = "2.0", status = STABLE)
        )
      )

      when(mockTpaConnector.deleteSubscriptions(*, *)(*)).thenReturn(successful(()))

      await(publisherService.publishAPIDefinition(testServiceLocation, retiredProducerApiDefinition)) shouldBe PublicationResult(approved = true, retiredPublisherResponse)

      verify(mockApiDefinitionConnector).publishAPI(*)(*)
      verify(mockApiSubscriptionFieldsConnector).publishFieldDefinitions(*)(*)
      verify(mockTpaConnector).deleteSubscriptions(eqTo("test"), eqTo("1.0"))(*)
      verify(mockTpaConnector, never).deleteSubscriptions(eqTo("test"), eqTo("2.0"))(*)
    }

    "Publish is successful with 2 Retired versions and 1 stable version" in new Setup {

      val retiredPublisherResponse = PublisherResponse(
        name = "Test",
        serviceName = "test",
        context = "test",
        description = "Test API",
        versions = List(
          PublisherApiVersion(version = "1.0", status = RETIRED),
          PublisherApiVersion(version = "2.0", status = RETIRED),
          PublisherApiVersion(version = "3.0", status = STABLE)
        )
      )

      when(mockTpaConnector.deleteSubscriptions(*, *)(*)).thenReturn(successful(()))

      await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinitionWith2RetiredVersions)) shouldBe PublicationResult(
        approved = true,
        retiredPublisherResponse
      )

      verify(mockApiDefinitionConnector).publishAPI(*)(*)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
      verify(mockTpaConnector).deleteSubscriptions(eqTo("test"), eqTo("1.0"))(*)
      verify(mockTpaConnector).deleteSubscriptions(eqTo("test"), eqTo("2.0"))(*)
      verify(mockTpaConnector, never).deleteSubscriptions(eqTo("test"), eqTo("3.0"))(*)
    }

    "Fail, propagating an error, when the tpaConnector fails" in new Setup {

      when(mockTpaConnector.deleteSubscriptions(*, *)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinitionWith2RetiredVersions))
      } shouldBe emulatedServiceError
    }

    "Retrieve the api from the microservice but don't Publish it to api-definition, api-subscription-fields and api-documentation if publication is not allowed" in new Setup {

      when(mockApprovalService.createOrUpdateServiceApproval(*)).thenReturn(successful(false))

      await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinition)) shouldBe PublicationResult(approved = false, publisherResponse)

      verifyZeroInteractions(mockApiDefinitionConnector)
      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "When publication allowed and api does not have subscription fields, publish API to api-definition and api-documentation only" in new Setup {
      await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinitionWithoutFieldDefinitions)) shouldBe PublicationResult(approved = true, publisherResponse)

      verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "Fail, propagating an error, when the apiDefinitionConnector fails" in new Setup {

      when(mockApiDefinitionConnector.publishAPI(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinition))
      } shouldBe emulatedServiceError
    }

    "Fail, propagating an error, when the apiSubscriptionFieldsConnector fails" in new Setup {

      when(mockApiSubscriptionFieldsConnector.publishFieldDefinitions(*)(*)).thenReturn(failed(emulatedServiceError))

      intercept[UnsupportedOperationException] {
        await(publisherService.publishAPIDefinition(testServiceLocation, producerApiDefinition))
      } shouldBe emulatedServiceError
    }
  }

  "validateAPIDefinition" should {

    "Succeed when no validation failures are detected" in new Setup {
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      await(publisherService.validation(producerApiDefinition, true))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

    }

    "Fail when Field Definition is invalid" in new Setup {

      val errorString = """{"error":"blah"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(Some(Json.parse(errorString))))

      val result: Option[JsValue] = await(publisherService.validation(producerApiDefinition, true))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"fieldDefinitionErrors":$errorString}"""
    }

    "Fail when api definition is invalid" in new Setup {
      val errorString = """{"error":"blah"}"""
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(Some(Json.parse("""{"error":"blah"}"""))))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val result: Option[JsValue] = await(publisherService.validation(producerApiDefinition, true))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe true
      Json.stringify(result.get) shouldBe s"""{"apiDefinitionErrors":$errorString}"""
    }

    "Succeed when status is retired but no API has subscriptions" in new Setup {
      when(mockApiDefinitionConnector.validateAPIDefinition(*)(*)).thenReturn(successful(None))
      when(mockApiSubscriptionFieldsConnector.validateFieldDefinitions(*)(*)).thenReturn(successful(None))

      val api: JsObject                                = Json.parse(getClass.getResourceAsStream("/input/api-with-retired-status.json")).as[JsObject]
      val producerApiDefinition: ProducerApiDefinition = ProducerApiDefinition(api)

      val result: Option[JsValue] = await(publisherService.validation(producerApiDefinition, true))

      verify(mockApiDefinitionConnector).validateAPIDefinition(*)(*)
      verify(mockApiSubscriptionFieldsConnector).validateFieldDefinitions(*)(*)

      result.isDefined shouldBe false
    }
  }
}
