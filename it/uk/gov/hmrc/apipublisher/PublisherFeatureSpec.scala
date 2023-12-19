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
import play.api.http.Status.UNPROCESSABLE_ENTITY
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{AUTHORIZATION, CONTENT_TYPE, JSON}
import play.api.test.TestServer
import scalaj.http.{Http, HttpResponse}
import uk.gov.hmrc.apipublisher.models.ErrorCode.INVALID_API_DEFINITION

class PublisherFeatureSpec extends BaseFeatureSpec {

  val publishingKey: String        = UUID.randomUUID().toString
  val encodedPublishingKey: String = new String(Base64.getEncoder.encode(publishingKey.getBytes), StandardCharsets.UTF_8)

  var server: TestServer = _

  Feature("Publish API on notification") {

    Scenario("Publisher receive an API notification") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJson)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.raml")).willReturn(aResponse().withBody(raml_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.raml")).willReturn(aResponse().withBody(raml_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.raml")).willReturn(aResponse().withBody(raml_3_0)))

      And("The api definition is running")
      // TOOD - restore when api definition no longer rejects updated api
      // apiDefinitionMock.register(post(urlEqualTo("/api-definition/validate")).willReturn(aResponse()))
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("The api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(post(urlEqualTo("/validate")).willReturn(aResponse()))

      And("The api scope is running")
      apiScopeMock.register(post(urlEqualTo("/scope")).willReturn(aResponse()))
      apiScopeMock.register(post(urlEqualTo("/scope/validate")).willReturn(aResponse()))
      apiScopeMock.register(get(urlEqualTo("/scope?keys=read:hello"))
        .willReturn(aResponse().withStatus(200).withBody(scopes)))

      When("The publisher is triggered")
      val publishResponse: HttpResponse[String] =
        Http(s"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .postData(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""").asString

      Then("The scope is validated")
      apiScopeMock.verifyThat(postRequestedFor(urlEqualTo("/scope/validate"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      Then("The field definitions are validated")
      apiSubscriptionFieldsMock.verifyThat(postRequestedFor(urlEqualTo("/validate"))
        .withHeader(CONTENT_TYPE, containing(JSON)))

      And("The scope is published to the API Scope microservice")
      apiScopeMock.verifyThat(postRequestedFor(urlEqualTo("/scope"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(scopes)))

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

      And("The api-publisher responded with status 2xx")
      publishResponse.is2xx shouldBe true
    }

    Scenario("Validation of API definition failed") {

      Given("A microservice is running with an invalid API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(invalidDefinitionJson)))

      When("The publisher is triggered")
      val publishResponse: HttpResponse[String] =
        Http(s"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .postData(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""").asString

      Then("The api-publisher responded with status 422")
      publishResponse.code shouldBe UNPROCESSABLE_ENTITY

      And("The validation errors are present in the response body")
      val responseBody: JsValue      = Json.parse(publishResponse.body)
      (responseBody \ "code").as[String] shouldBe INVALID_API_DEFINITION.toString
      val errorMessages: Seq[String] = (responseBody \ "message" \ "causingExceptions" \\ "message").map(_.as[String]).toSeq
      errorMessages should contain.only(
        """string [read:HELLO] does not match pattern ^[a-z:\-0-9]+$""",
        """string [c] does not match pattern ^[a-z]+[a-z/\-]{4,}$"""
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

  val invalidDefinitionJson =
    s"""
       |{
       |  "scopes": [
       |    {
       |      "key": "read:HELLO",
       |      "name": "Say Hello",
       |      "description": "Ability to Say Hello"
       |    }
       |  ],
       |  "api": {
       |    "name": "Test",
       |    "description": "Test API",
       |    "context": "c",
       |    "versions": [
       |      {
       |        "version": "1.0",
       |        "status": "PUBLISHED"
       |      }
       |    ]
       |  }
       |}
    """.stripMargin

  val definitionJson =
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
