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

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode

import play.api.http.Status.NOT_FOUND
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.Helpers.{AUTHORIZATION, CONTENT_TYPE, JSON}
import play.api.test.TestServer

import uk.gov.hmrc.apipublisher.models.ErrorCode

class PublisherFeatureSpec extends BaseFeatureSpec with EitherValues {

  val publishingKey: String        = UUID.randomUUID().toString
  val encodedPublishingKey: String = new String(Base64.getEncoder.encode(publishingKey.getBytes), StandardCharsets.UTF_8)

  var server: TestServer = _

  Feature("Publish API on notification") {

    Scenario("Publishing successful for an API with a valid definition") {

      Given("A microservice is running with an API Definition without scopes")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithoutScopes)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.raml")).willReturn(aResponse().withBody(raml_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.raml")).willReturn(aResponse().withBody(raml_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.raml")).willReturn(aResponse().withBody(raml_3_0)))

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

      And("api-publisher responded with status 2xx")
      publishResponse.isSuccess shouldBe true
    }

    Scenario("A validation error occurs during Publish due to scopes in definition") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJsonWithScopes)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.raml")).willReturn(aResponse().withBody(raml_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.raml")).willReturn(aResponse().withBody(raml_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.raml")).willReturn(aResponse().withBody(raml_3_0)))

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
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.raml")).willReturn(aResponse().withBody(raml_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.raml")).willReturn(aResponse().withBody(raml_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.raml")).willReturn(aResponse().withBody(raml_3_0)))

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

  override def beforeEach(): Unit = {
    super.beforeEach()
    server = TestServer.apply(port, GuiceApplicationBuilder().configure("publishingKey" -> publishingKey).build())
    server.start()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    server.stop()
  }

  val apiContext                          = "test/api/context"
  val urlEncodedApiContext                = "test%2Fapi%2Fcontext"
  val apiSubscriptionFieldsUrlVersion_1_0 = s"/definition/context/$urlEncodedApiContext/version/1.0"
  val apiSubscriptionFieldsUrlVersion_2_0 = s"/definition/context/$urlEncodedApiContext/version/2.0"
  val apiSubscriptionFieldsUrlVersion_3_0 = s"/definition/context/$urlEncodedApiContext/version/3.0"

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
       |        "status": "PUBLISHED"
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
       |        "status": "PUBLISHED",
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
       |        "status": "PUBLISHED"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "PUBLISHED",
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
       |        "status": "PUBLISHED",
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
       |        "status": "PUBLISHED"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "PUBLISHED",
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

  val definitionJsonWithoutScopes =
    s"""
       |{
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "$apiContext",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "PUBLISHED",
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
       |        "status": "PUBLISHED"
       |      },
       |      {
       |        "version": "3.0",
       |        "status": "PUBLISHED",
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

  val api =
    s"""
       |{
       |  "serviceName" : "test.example.com",
       |  "serviceBaseUrl" : "http://127.0.0.1:21112",
       |  "name" : "Test",
       |  "description" : "Test API",
       |  "context" : "$apiContext",
       |  "versions" : [
       |    {
       |      "version" : "1.0",
       |      "status" : "PUBLISHED",
       |        "endpoints": [
       |          {
       |            "uriPattern": "/hello",
       |            "endpointName":"Say Hello",
       |            "method": "GET",
       |            "authType": "NONE",
       |            "throttlingTier": "UNLIMITED"
       |          }
       |        ]
       |    },
       |    {
       |      "version" : "2.0",
       |      "status" : "PUBLISHED",
       |        "endpoints": [
       |          {
       |            "uriPattern": "/hello",
       |            "endpointName":"Say Hello",
       |            "method": "GET",
       |            "authType": "NONE",
       |            "throttlingTier": "UNLIMITED",
       |            "scope": "read:hello"
       |          }
       |        ]
       |    },
       |    {
       |      "version" : "3.0",
       |      "status" : "PUBLISHED",
       |        "endpoints": [
       |          {
       |            "uriPattern": "/hello",
       |            "endpointName":"Say Hello",
       |            "method": "GET",
       |            "authType": "NONE",
       |            "throttlingTier": "UNLIMITED",
       |            "scope": "read:hello"
       |          }
       |        ]
       |    }
       |  ]
       |}
    """.stripMargin

  val scopes =
    """
      |[
      |    {
      |      "key": "read:hello",
      |      "name": "Say Hello",
      |      "description": "Ability to Say Hello"
      |    }
      |]
    """.stripMargin

  val apiDocumentationRegistration =
    """
      |{
      |  "serviceName": "test.example.com",
      |  "serviceUrl": "http://127.0.0.1:21112",
      |  "serviceVersions": [ "1.0", "2.0", "3.0" ]
      |}
    """.stripMargin

  val raml_1_0 =
    """
      |#%RAML 1.0
      |---
      |title: Hello World
      |version: 1.0
      |
      |/hello:
      |  get:
      |    displayName: "Say Hello"
    """.stripMargin

  val raml_2_0 =
    """
      |#%RAML 1.0
      |---
      |title: Hello World
      |version: 2.0
      |
      |annotationTypes:
      |  scope:
      |
      |/hello:
      |  get:
      |    displayName: "Say Hello"
      |    (scope): "read:hello"
      |
    """.stripMargin

  val raml_3_0 =
    """
      |#%RAML 1.0
      |---
      |title: Hello World
      |version: 3.0
      |
      |annotationTypes:
      |  scope:
      |
      |/hello:
      |  get:
      |    displayName: "Say Hello"
      |    (scope): "read:hello"
      |
    """.stripMargin
}
