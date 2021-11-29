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

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{AUTHORIZATION, CONTENT_TYPE, JSON}
import play.api.test.TestServer

import scalaj.http
import scalaj.http.Http
import play.api.http.Status.BAD_REQUEST

class ValidateFeatureSpec extends BaseFeatureSpec {

  val publishingKey: String = UUID.randomUUID().toString
  val encodedPublishingKey: String = new String(Base64.getEncoder.encode(publishingKey.getBytes), StandardCharsets.UTF_8)

  var server: TestServer = _

  val malformedJson = """{ "some" "invalid"" "json" }"""

  Feature("Validate should fail when given malformed json") {
    Scenario("The /validate endpoint is called with malformed Json") {
      Given("The API Publisher is running")
      startServer()
      When("malformed Json is passed to an endpoint")
      val response: http.HttpResponse[String] = Http(s"$serverUrl/validate").header(CONTENT_TYPE, JSON).postData(malformedJson).asString
      Then("The controller should return 400 with a Malformed Json error message")
      response.code shouldBe BAD_REQUEST
      val body = Json.parse( response.body )
      (body \ "statusCode").as[Int] shouldBe BAD_REQUEST
      (body \ "message").as[String] should startWith("Invalid Json")
    }
  }

  Feature("Validate should fail when API Definition service responds with errors") {
    Scenario("The /validate endpoint is called") {

      Given("the API Publisher is running")
      startServer()

      And("the API Definition service is primed to respond with an error")
      apiDefinitionMock.register(post(urlEqualTo("/api-definition/validate"))
        .willReturn(aResponse().withStatus(400).withBody("""{"error":"invalid"}""")))

      And("the API Scope service is primed to respond to a 'keys' query with a success")
      apiScopeMock.register(get(urlEqualTo("/scope?keys=read:hello"))
        .willReturn(aResponse().withStatus(200).withBody(scopes)))

      And("the API Scope service is primed to respond with a success")
      apiScopeMock.register(post(urlEqualTo("/scope/validate"))
        .willReturn(aResponse().withStatus(204)))

      When("a Json payload is passed to the validate endpoint")
      val response: http.HttpResponse[String] =
        Http(s"$serverUrl/validate")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .postData(apiAndScope).asString

      Then("the controller should return 400 with the API Definition error message")
      assert(response.code == 400)
      val body = Json.parse(response.body)
      assert((body \ "apiDefinitionErrors").as[String] contains """'{"error":"invalid"}'""")
    }
  }

  override def afterEach(): Unit = {
    super.afterEach()
    stopServer()
  }

  private def startServer(): Unit = {
    server = TestServer.apply(port, GuiceApplicationBuilder().configure("publishingKey" -> publishingKey).build())
    server.start()
  }

  def stopServer(): Unit = {
    server.stop()
  }

  val apiAndScope: String =
    """
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
      |    "context": "test",
      |    "versions": [
      |      {
      |        "version": "1.0",
      |        "status": "PUBLISHED",
      |        "endpoints": [
      |          {
      |            "uriPattern": "/hello",
      |            "endpointName": "Say Hello",
      |            "method": "GET",
      |            "authType": "NONE",
      |            "throttlingTier": "UNLIMITED"
      |          }
      |        ]
      |      },
      |      {
      |        "version": "2.0",
      |        "status": "PUBLISHED",
      |        "endpoints": [
      |          {
      |            "uriPattern": "/hello",
      |            "endpointName": "Say Hello",
      |            "method": "GET",
      |            "authType": "NONE",
      |            "throttlingTier": "UNLIMITED",
      |            "scope": "read:hello"
      |          }
      |        ]
      |      }
      |    ]
      |  }
      |}
    """.stripMargin

  val scopes: String =
    """
      |  [
      |    {
      |      "key": "read:hello",
      |      "name": "Say Hello",
      |      "description": "Ability to Say Hello"
      |    }
      |  ]
    """.stripMargin

}
