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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{CONTENT_TYPE, JSON}
import play.api.test.TestServer

class RegisterFeatureSpec extends BaseFeatureSpec {

  private var server: TestServer = _

  feature("Register to the service locator") {

    scenario("Register to the service locator when booting up") {

      Given("The service locator is running")
      serviceLocatorMock.register(post(urlEqualTo("/subscription")).willReturn(aResponse()))

      When("The API Publisher boots up")
      startServer()

      Then("The API Publisher registers to the service locator")
      serviceLocatorMock.verifyThat(postRequestedFor(urlEqualTo("/subscription"))
        .withHeader(CONTENT_TYPE, containing(JSON))
        .withRequestBody(equalToJson(expectedSubscriptionRequest)))
    }
  }

  override def afterEach(): Unit = {
    super.afterEach()
    stopServer()
  }

  private def startServer(): Unit = {
    server = new TestServer(port, GuiceApplicationBuilder().build())
    server.start()
  }

  private def stopServer(): Unit = {
    server.stop()
  }

  private val appUrl = "http://localhost:9603"

  private val expectedSubscriptionRequest =
    s"""
      |{
      |  "serviceName":"api-publisher",
      |  "callbackUrl":"$appUrl/publish",
      |  "criteria": {
      |    "third-party-api":"true"
      |  }
      |}
    """.stripMargin

}
