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

package uk.gov.hmrc.apipublisher

import scala.concurrent.Await.result
import scala.concurrent.duration._
import scala.language.postfixOps

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode

import play.api.http.Status.NOT_FOUND
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.Helpers.{AUTHORIZATION, CONTENT_TYPE, JSON}

import uk.gov.hmrc.apipublisher.models.ApprovalStatus.{APPROVED, FAILED, RESUBMITTED}
import uk.gov.hmrc.apipublisher.models.{APIApproval, ErrorCode}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository

class PublisherFeatureSpec extends BaseFeatureSpec
    with EitherValues {

  Feature("Publish API on notification") {

    Scenario("Publishing successful for an API with a valid definition and OAS") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJson)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      And("api-publisher responded with status 202")
      publishResponse.code shouldBe StatusCode.Accepted

      When("API is approved")
      val approveResponse = http(
        basicRequest
          .post(uri"$serverUrl/service/test.example.com/approve")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "actor": {"user": "Gatekeeper User"} }""")
      )

      Then("approval response is status 2xx")
      approveResponse.isSuccess shouldBe true

      Then("The field definitions are validated")
      apiSubscriptionFieldsMock.verifyThat(postRequestedFor(urlEqualTo("/validate"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      Then("The definition is published to the API Definition microservice")
      apiDefinitionMock.verifyThat(postRequestedFor(urlEqualTo("/api-definition"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      Then("The field definitions are published to the API Subscription Fields microservice")
      apiSubscriptionFieldsMock.verifyThat(putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(fieldDefinitions_1_0)))

      apiSubscriptionFieldsMock.verifyThat(0, putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_2_0)))

      apiSubscriptionFieldsMock.verifyThat(putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(fieldDefinitions_3_0)))

      result(repository.asInstanceOf[APIApprovalRepository].fetch("test.example.com"), 10 seconds) match {
        case None                        => fail()
        case Some(approval: APIApproval) => approval.status == APPROVED
      }

      When("publisher is triggered")
      val publishResponse2 = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      And("api-publisher responded with status 200")
      publishResponse2.code shouldBe StatusCode.Ok

    }

    Scenario("NEW API Approval is declined") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJson)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The field definitions are validated")
      apiSubscriptionFieldsMock.verifyThat(postRequestedFor(urlEqualTo("/validate"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      And("api-publisher responded with status 202")
      publishResponse.code shouldBe StatusCode.Accepted

      When("API is declined")
      val declineResponse = http(
        basicRequest
          .post(uri"$serverUrl/service/test.example.com/decline")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "actor": {"user": "Gatekeeper User"} }""")
      )

      Then("decline response is status 2xx")
      declineResponse.isSuccess shouldBe true

      Then("The definition is not published to the API Definition microservice")
      apiDefinitionMock.verifyThat(
        0,
        postRequestedFor(urlEqualTo("/api-definition"))
          .withHeader(CONTENT_TYPE, containing(JSON))
      )

      Then("The field definitions are not published to the API Subscription Fields microservice")
      apiSubscriptionFieldsMock.verifyThat(
        0,
        putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0))
          .withHeader(CONTENT_TYPE, containing(JSON))
          .withRequestBody(equalToJson(fieldDefinitions_1_0))
      )

      apiSubscriptionFieldsMock.verifyThat(0, putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_2_0)))

      apiSubscriptionFieldsMock.verifyThat(
        0,
        putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0))
          .withHeader(CONTENT_TYPE, containing(JSON))
          .withRequestBody(equalToJson(fieldDefinitions_3_0))
      )

      result(repository.asInstanceOf[APIApprovalRepository].fetch("test.example.com"), 10 seconds) match {
        case None                        => fail()
        case Some(approval: APIApproval) => approval.status == FAILED
      }
    }

    Scenario("NEW API Approval is declined then RESUBMITTED and finally APPROVED and successfully published") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJson)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      And("api-publisher responded with status 202")
      publishResponse.code shouldBe StatusCode.Accepted

      When("API is declined")
      val declineResponse = http(
        basicRequest
          .post(uri"$serverUrl/service/test.example.com/decline")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "actor": {"user": "Gatekeeper User"} }""")
      )

      Then("decline response is status 2xx")
      declineResponse.isSuccess shouldBe true

      And("API Approval has status of FAILED")
      result(repository.asInstanceOf[APIApprovalRepository].fetch("test.example.com"), 10 seconds) match {
        case None                        => fail()
        case Some(approval: APIApproval) => approval.status == FAILED
      }

      When("publisher is triggered again")
      val publishResponse2 = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      And("API Approval has status of RESUBMITTED")
      result(repository.asInstanceOf[APIApprovalRepository].fetch("test.example.com"), 10 seconds) match {
        case None                        => fail()
        case Some(approval: APIApproval) => approval.status == RESUBMITTED
      }

      And("api-publisher responded with status 202")
      publishResponse2.code shouldBe StatusCode.Accepted

      When("API is approved")
      val approveResponse = http(
        basicRequest
          .post(uri"$serverUrl/service/test.example.com/approve")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "actor": {"user": "Gatekeeper User"} }""")
      )

      Then("approval response is status 2xx")
      approveResponse.isSuccess shouldBe true

      And("API Approval has status of APPROVED")
      result(repository.asInstanceOf[APIApprovalRepository].fetch("test.example.com"), 10 seconds) match {
        case None                        => fail()
        case Some(approval: APIApproval) => approval.status == APPROVED
      }

      When("publisher is triggered")
      val publishResponse3 = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      And("api-publisher responded with status 200")
      publishResponse3.code shouldBe StatusCode.Ok
    }

    Scenario("Publishing fails for a new API with a valid definition and OAS") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJson)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The field definitions are validated")
      apiSubscriptionFieldsMock.verifyThat(postRequestedFor(urlEqualTo("/validate"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      Then("No calls to API Definition microservice were sent")
      apiDefinitionMock.verifyThat(
        0,
        postRequestedFor(urlEqualTo("/api-definition"))
          .withHeader(CONTENT_TYPE, containing(JSON))
      )

      Then("The field definitions are not published to the API Subscription Fields microservice")
      apiSubscriptionFieldsMock.verifyThat(
        0,
        putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0))
          .withHeader(CONTENT_TYPE, containing(JSON))
          .withRequestBody(equalToJson(fieldDefinitions_1_0))
      )

      apiSubscriptionFieldsMock.verifyThat(0, putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_2_0)))

      apiSubscriptionFieldsMock.verifyThat(
        0,
        putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0))
          .withHeader(CONTENT_TYPE, containing(JSON))
          .withRequestBody(equalToJson(fieldDefinitions_3_0))
      )

      And("api-publisher responded with status 2xx")
      publishResponse.isSuccess shouldBe true
    }

    Scenario("Publishing successful for an API with a RETIRED version") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithRetiredVersion)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      And("third party application is running")
      tpaMock.register(delete(urlEqualTo(tpaVersion_1_0)).willReturn(aResponse()))
      tpaMock.register(delete(urlEqualTo(tpaVersion_2_0)).willReturn(aResponse()))
      tpaMock.register(delete(urlEqualTo(tpaVersion_3_0)).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      When("API is approved")
      val approveResponse = http(
        basicRequest
          .post(uri"$serverUrl/service/test.example.com/approve")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "actor": {"user": "Gatekeeper User"} }""")
      )

      Then("approval response is status 2xx")
      approveResponse.isSuccess shouldBe true

      Then("The field definitions are validated")
      apiSubscriptionFieldsMock.verifyThat(postRequestedFor(urlEqualTo("/validate"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      Then("The definition is published to the API Definition microservice")
      apiDefinitionMock.verifyThat(postRequestedFor(urlEqualTo("/api-definition"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      Then("The field definitions are published to the API Subscription Fields microservice")
      apiSubscriptionFieldsMock.verifyThat(putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(fieldDefinitions_1_0)))

      apiSubscriptionFieldsMock.verifyThat(0, putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_2_0)))

      apiSubscriptionFieldsMock.verifyThat(putRequestedFor(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(fieldDefinitions_3_0)))

      Then("The subscriptions for RETIRED versions are deleted")
      tpaMock.verifyThat(deleteRequestedFor(urlEqualTo(tpaVersion_1_0)))

      tpaMock.verifyThat(0, deleteRequestedFor(urlEqualTo(tpaVersion_2_0)))

      tpaMock.verifyThat(0, deleteRequestedFor(urlEqualTo(tpaVersion_3_0)))

      And("api-publisher responded with status 2xx")
      publishResponse.isSuccess shouldBe true
    }

    Scenario("A validation error occurs during Publish due to scopes in definition") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithScopes)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      // apiDefinitionMock.register(post(urlEqualTo("/api-definition/validate")).willReturn(aResponse()))
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = (responseBody \ "message" \ "message").as[String]
      errorMessages shouldBe """extraneous key [scopes] is not permitted"""
    }

    Scenario("A validation error occurs during Publish due to empty scopes in definition") {

      Given("A microservice is running with an API Definition with empty scopes")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithEmptyScopes)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = (responseBody \ "message" \ "message").as[String]
      errorMessages shouldBe """extraneous key [scopes] is not permitted"""
    }

    Scenario("A validation error occurs during Publish due to invalid context in definition") {

      Given("A microservice is running with an invalid API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithInvalidContext)))

      When("The publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = (responseBody \ "message" \ "message").as[String]
      errorMessages shouldBe """string [invalid context] does not match pattern ^[a-z]+[a-z/\-]{4,}$"""
    }

    Scenario("A validation error occurs during Publish due to missing FieldDefinition name") {

      Given("A microservice is running with an API Definition with empty scopes")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithNoFieldDefinitionName)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = ((responseBody \ "message" \ "causingExceptions")(0) \ "message").as[String]
      errorMessages shouldBe """required key [name] not found"""
    }

    Scenario("A validation error occurs during Publish due to missing regex attribute in RegexValidationRule") {

      Given("A microservice is running with an API Definition with empty scopes")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithNoRegexAttribute)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = ((responseBody \ "message" \ "causingExceptions")(0) \ "message").as[String]
      errorMessages shouldBe """required key [regex] not found"""
    }

    Scenario("A validation error occurs during Publish due to an empty rules array") {

      Given("A microservice is running with an API Definition with empty scopes")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithEmptyRulesArray)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = ((responseBody \ "message" \ "causingExceptions")(0) \ "message").as[String]
      errorMessages shouldBe """expected minimum item count: 1, found: 0"""
    }

    Scenario("A validation error occurs during Publish due to missing errorMessage") {

      Given("A microservice is running with an API Definition with empty scopes")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithNoErrorMessageAttribute)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.yaml")).willReturn(aResponse().withBody(oas_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.yaml")).willReturn(aResponse().withBody(oas_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.yaml")).willReturn(aResponse().withBody(oas_3_0)))

      And("api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      When("publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe StatusCode.UnprocessableEntity

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      (responseBody \ "code").as[String] shouldBe ErrorCode.INVALID_API_DEFINITION.toString
      val errorMessages         = ((responseBody \ "message" \ "causingExceptions")(0) \ "message").as[String]
      errorMessages shouldBe """required key [errorMessage] not found"""
    }

    Scenario("When fetch of definition.json file from microservice fails with NOT_FOUND") {

      Given("A microservice is running with an invalid API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withStatus(NOT_FOUND)))

      When("The publisher is triggered")
      val publishResponse = http(
        basicRequest
          .post(uri"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""")
      )

      Then("The api-publisher responded with status BAD_REQUEST")
      publishResponse.code shouldBe StatusCode.BadRequest

      And("The validation errors are present in the response body")
      val responseBody: JsValue = Json.parse(publishResponse.body.left.value)
      responseBody shouldBe Json.obj(
        "code"    -> JsString(ErrorCode.INVALID_API_DEFINITION.toString),
        "message" -> JsString("Unable to find definition for service test.example.com")
      )
    }
  }

  val apiContext                          = "test/api/context"
  val urlEncodedApiContext                = "test%2Fapi%2Fcontext"
  val apiSubscriptionFieldsUrlVersion_1_0 = s"/definition/context/$urlEncodedApiContext/version/1.0"
  val apiSubscriptionFieldsUrlVersion_2_0 = s"/definition/context/$urlEncodedApiContext/version/2.0"
  val apiSubscriptionFieldsUrlVersion_3_0 = s"/definition/context/$urlEncodedApiContext/version/3.0"
  val tpaVersion_1_0                      = s"/apis/$apiContext/versions/1.0/subscribers"
  val tpaVersion_2_0                      = s"/apis/$apiContext/versions/2.0/subscribers"
  val tpaVersion_3_0                      = s"/apis/$apiContext/versions/3.0/subscribers"

  val definitionJsonWithInvalidContext =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "invalid context",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE"
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithScopes =
    s"""
       |{
       |  "scopes": [
       |    {
       |      "key": "read:hello",
       |      "name": "Say Hello",
       |      "description": "Ability to Say Hello"
       |    }
       |  ],
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "Callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          },
       |          {
       |            "name": "token",
       |            "description": "Secure Token",
       |            "hint": "Just a hint",
       |            "type": "SecureToken"
       |          }
       |        ]
       |      },
       |      {
       |        "version": "2.0",
       |        "status": "STABLE"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrlOnly",
       |            "description": "Only a callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithEmptyScopes =
    s"""
       |{
       |  "scopes": [
       |  ],
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "Callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          },
       |          {
       |            "name": "token",
       |            "description": "Secure Token",
       |            "hint": "Just a hint",
       |            "type": "SecureToken"
       |          }
       |        ]
       |      },
       |      {
       |        "version": "2.0",
       |        "status": "STABLE"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrlOnly",
       |            "description": "Only a callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithNoFieldDefinitionName =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |      "fieldDefinitions": [
       |          {
       |            "description": "What is your callback URL?",
       |            "shortDescription": "Callback URL",
       |            "type": "PPNSField",
       |            "validation": {
       |              "errorMessage": "Callback URL must be a valid https URL",
       |              "rules": [
       |                {
       |                  "UrlValidationRule": {}
       |                },
       |                {
       |                  "RegexValidationRule": {
       |                    "regex": "^https.*"
       |                  }
       |                }
       |              ]
       |            }
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithNoRegexAttribute =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |      "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "What is your callback URL?",
       |            "shortDescription": "Callback URL",
       |            "type": "PPNSField",
       |            "validation": {
       |              "errorMessage": "Callback URL must be a valid https URL",
       |              "rules": [
       |                {
       |                  "UrlValidationRule": {}
       |                },
       |                {
       |                  "RegexValidationRule": {}
       |                }
       |              ]
       |            }
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithEmptyRulesArray =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |      "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "What is your callback URL?",
       |            "shortDescription": "Callback URL",
       |            "type": "PPNSField",
       |            "validation": {
       |              "errorMessage": "Callback URL must be a valid https URL",
       |              "rules": [
       |              ]
       |            }
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithNoErrorMessageAttribute =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |      "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "What is your callback URL?",
       |            "shortDescription": "Callback URL",
       |            "type": "PPNSField",
       |            "validation": {
       |              "rules": [
       |                {
       |                  "UrlValidationRule": {}
       |                },
       |                {
       |                  "RegexValidationRule": {
       |                    "regex": "^https.*"
       |                  }
       |                }
       |              ]
       |            }
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJson =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "Callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          },
       |          {
       |            "name": "token",
       |            "description": "Secure Token",
       |            "hint": "Just a hint",
       |            "type": "SecureToken"
       |          }
       |        ]
       |      },
       |      {
       |        "version": "2.0",
       |        "status": "STABLE"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrlOnly",
       |            "description": "Only a callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJsonWithRetiredVersion =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "RETIRED",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrl",
       |            "description": "Callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          },
       |          {
       |            "name": "token",
       |            "description": "Secure Token",
       |            "hint": "Just a hint",
       |            "type": "SecureToken"
       |          }
       |        ]
       |      },
       |      {
       |        "version": "2.0",
       |        "status": "STABLE"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "STABLE",
       |        "fieldDefinitions": [
       |          {
       |            "name": "callbackUrlOnly",
       |            "description": "Only a callback URL",
       |            "hint": "Just a hint",
       |            "type": "URL"
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val fieldDefinitions_1_0 =
    """
      |{
      |  "fieldDefinitions": [
      |    {
      |      "name": "callbackUrl",
      |      "description": "Callback URL",
      |      "hint": "Just a hint",
      |      "type": "URL"
      |    },
      |    {
      |      "name": "token",
      |      "description": "Secure Token",
      |      "hint": "Just a hint",
      |      "type": "SecureToken"
      |    }
      |  ]
      |}
    """.stripMargin

  val fieldDefinitions_3_0 =
    """
      |{
      |  "fieldDefinitions": [
      |    {
      |      "name": "callbackUrlOnly",
      |      "description": "Only a callback URL",
      |      "hint": "Just a hint",
      |      "type": "URL"
      |    }
      |  ]
      |}
    """.stripMargin

  val apiDocumentationRegistration =
    """
      |{
      |  "serviceName": "test.example.com",
      |  "serviceUrl": "http://127.0.0.1:21112",
      |  "serviceVersions": [ "1.0", "2.0", "3.0" ]
      |}
    """.stripMargin

  val oas_1_0 =
    """openapi: "3.0.3"
      |info:
      |  title: API Platform Test
      |  version: 1.0.0
      |  contact: {}
      |servers:
      |  - url: https://test-api.service.hmrc.gov.uk/test/api-platform-test
      |    description: Sandbox
      |  - url: https://api.service.hmrc.gov.uk/test/api-platform-test
      |    description: Production
      |    variables: {}
      |
      |components:
      |  parameters:
      |    acceptHeader:
      |      name: Accept
      |      in: header
      |      schema:
      |        type: string
      |        enum: [
      |          "application/vnd.hmrc.1.0+json"
      |        ]
      |      required: true
      |paths:
      |  /check/location:
      |    get:
      |      summary: Get Check Location
      |      description: |
      |        Get Check Location.
      |        This endpoint is open access and requires no Authorization header.
      |      tags:
      |        - api-platform-test
      |      parameters:
      |        - $ref: '#/components/parameters/acceptHeader'
      |      responses:
      |        200:
      |          description: "OK Response"
      |      security:
      |        - {}""".stripMargin

  val oas_2_0 =
    """openapi: "3.0.3"
      |info:
      |  title: API Platform Test
      |  version: 2.0.0
      |  contact: {}
      |servers:
      |  - url: https://test-api.service.hmrc.gov.uk/test/api-platform-test
      |    description: Sandbox
      |  - url: https://api.service.hmrc.gov.uk/test/api-platform-test
      |    description: Production
      |    variables: {}
      |
      |components:
      |  parameters:
      |    acceptHeader:
      |      name: Accept
      |      in: header
      |      schema:
      |        type: string
      |        enum: [
      |          "application/vnd.hmrc.2.0+json"
      |        ]
      |      required: true
      |paths:
      |  /check/location:
      |    get:
      |      summary: Get Check Location
      |      description: |
      |        Get Check Location.
      |        This endpoint is open access and requires no Authorization header.
      |      tags:
      |        - api-platform-test
      |      parameters:
      |        - $ref: '#/components/parameters/acceptHeader'
      |      responses:
      |        200:
      |          description: "OK Response"
      |      security:
      |        - {}""".stripMargin

  val oas_3_0 =
    """openapi: "3.0.3"
      |info:
      |  title: API Platform Test
      |  version: 1.0.0
      |  contact: {}
      |servers:
      |  - url: https://test-api.service.hmrc.gov.uk/test/api-platform-test
      |    description: Sandbox
      |  - url: https://api.service.hmrc.gov.uk/test/api-platform-test
      |    description: Production
      |    variables: {}
      |
      |components:
      |  parameters:
      |    acceptHeader:
      |      name: Accept
      |      in: header
      |      schema:
      |        type: string
      |        enum: [
      |          "application/vnd.hmrc.3.0+json"
      |        ]
      |      required: true
      |paths:
      |  /check/location:
      |    get:
      |      summary: Get Check Location
      |      description: |
      |        Get Check Location.
      |        This endpoint is open access and requires no Authorization header.
      |      tags:
      |        - api-platform-test
      |      parameters:
      |        - $ref: '#/components/parameters/acceptHeader'
      |      responses:
      |        200:
      |          description: "OK Response"
      |      security:
      |        - {}""".stripMargin
}
