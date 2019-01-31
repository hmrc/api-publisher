/*
 * Copyright 2018 HM Revenue & Customs
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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{CONTENT_TYPE, JSON, AUTHORIZATION}
import play.api.test.TestServer

import scalaj.http.{Http, HttpResponse}

class PublisherFeatureSpec extends BaseFeatureSpec {

  val publishingKey: String = UUID.randomUUID().toString

  var server: TestServer = _

  feature("Publish API on notification") {

    scenario("Publisher receive an API notification") {

      Given("A microservice is running with an API Definition")
      apiProducerMock.register(get(urlEqualTo("/api/definition")).willReturn(aResponse().withBody(definitionJson)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/1.0/application.raml")).willReturn(aResponse().withBody(raml_1_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/2.0/application.raml")).willReturn(aResponse().withBody(raml_2_0)))
      apiProducerMock.register(get(urlEqualTo("/api/conf/3.0/application.raml")).willReturn(aResponse().withBody(raml_3_0)))

      And("The api definition is running")
      apiDefinitionMock.register(post(urlEqualTo("/api-definition")).willReturn(aResponse()))

      And("The api subscription fields is running")
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_1_0)).willReturn(aResponse()))
      apiSubscriptionFieldsMock.register(put(urlEqualTo(apiSubscriptionFieldsUrlVersion_3_0)).willReturn(aResponse()))

      And("The api scope is running")
      apiScopeMock.register(post(urlEqualTo("/scope")).willReturn(aResponse()))

      When("The service locator triggers the publisher")
      val publishResponse: HttpResponse[String] =
        Http(s"$serverUrl/publish")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, publishingKey)
          .postData(s"""{"serviceName":"test.example.com", "serviceUrl": "$apiProducerUrl", "metadata": { "third-party-api" : "true" } }""").asString

      Then("The definition is published to the API Definition microservice")
      apiDefinitionMock.verifyThat(postRequestedFor(urlEqualTo("/api-definition"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(api)))

      And("The scope is published to the API Scope microservice")
      apiScopeMock.verifyThat(postRequestedFor(urlEqualTo("/scope"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(scopes)))

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
  }

  override def beforeEach() {
    super.beforeEach()
    serviceLocatorMock.register(post(urlEqualTo("/subscription")).willReturn(aResponse()))

    server = new TestServer(port, GuiceApplicationBuilder().configure("publishingKey" -> publishingKey).build())
    server.start()
  }

  override def afterEach() {
    super.afterEach()
    server.stop()
  }

  val apiContext = "test/api/context"
  val urlEncodedApiContext = "test%2Fapi%2Fcontext"
  val apiSubscriptionFieldsUrlVersion_1_0 = s"/definition/context/$urlEncodedApiContext/version/1.0"
  val apiSubscriptionFieldsUrlVersion_2_0 = s"/definition/context/$urlEncodedApiContext/version/2.0"
  val apiSubscriptionFieldsUrlVersion_3_0 = s"/definition/context/$urlEncodedApiContext/version/3.0"

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
      |            "name": "callback-url",
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
      |            "name": "callback-url-only",
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
      |      "name": "callback-url",
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
     |      "name": "callback-url-only",
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
