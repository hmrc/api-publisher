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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.Json
import play.api.test.Helpers.{AUTHORIZATION, CONTENT_TYPE, JSON}
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode

class ValidateFeatureSpec extends BaseFeatureSpec with EitherValues {


  val malformedJson = """{ "some" "invalid"" "json" }"""

  Feature("Validate should fail when given malformed json") {
    Scenario("The /validate endpoint is called with malformed Json") {
      Given("The API Publisher is running")
      When("malformed Json is passed to an endpoint")
      val response = http(
        basicRequest
          .post(uri"$serverUrl/validate")
          .header(CONTENT_TYPE, JSON)
          .body(malformedJson)
      )
      Then("The controller should return 400 with a Malformed Json error message")
      response.code shouldBe StatusCode.BadRequest
      val body     = Json.parse(response.body.left.value)
      (body \ "statusCode").as[Int] shouldBe BAD_REQUEST
      (body \ "message").as[String] should startWith("Invalid Json")
    }
  }

  Feature("Validate should fail when API Definition service responds with errors") {
    Scenario("The /validate endpoint is called") {

      Given("the API Publisher is running")

      And("the API Definition service is primed to respond with an error")
      apiDefinitionMock.register(post(urlEqualTo("/api-definition/validate"))
        .willReturn(aResponse().withStatus(400).withBody("""{"error":"invalid"}""")))

      When("a Json payload is passed to the validate endpoint")
      val response = http(
        basicRequest
          .post(uri"$serverUrl/validate")
          .header(CONTENT_TYPE, JSON)
          .header(AUTHORIZATION, encodedPublishingKey)
          .body(producerApiDefinitionWithScope)
      )

      Then("the controller should return 400 with the API Definition error message")
      assert(response.code == StatusCode.BadRequest)
      val body = Json.parse(response.body.left.value)
      assert((body \ "apiDefinitionErrors").as[String] contains """'{"error":"invalid"}'""")
    }
  }


  val producerApiDefinitionWithScope: String =
    """
      |{
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
}
