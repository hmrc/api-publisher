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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

abstract class BaseFeatureSpec extends AnyFeatureSpec
  with GivenWhenThen with ScalaFutures
  with BeforeAndAfterEach with Matchers {

  val port = 12121
  val serverUrl = s"http://localhost:$port"

  val apiDefinitionPort: Int = sys.env.getOrElse("WIREMOCK", "9604").toInt
  val apiDefinitionHost = "localhost"
  var apiDefinitionUrl = s"http://$apiDefinitionHost:$apiDefinitionPort"
  val apiDefinitionServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDefinitionPort))
  var apiDefinitionMock : WireMock = _

  val apiProducerPort: Int = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiProducerHost = "127.0.0.1"
  val apiProducerUrl = s"http://$apiProducerHost:$apiProducerPort"
  val apiProducerServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiProducerPort))
  var apiProducerMock : WireMock = _

  val apiScopePort: Int = sys.env.getOrElse("WIREMOCK", "9690").toInt
  val apiScopeHost = "localhost"
  var apiScopeUrl = s"http://$apiScopeHost:$apiScopePort"
  val apiScopeServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiScopePort))
  var apiScopeMock : WireMock = _

  val apiSubscriptionFieldsPort: Int = sys.env.getOrElse("WIREMOCK", "9650").toInt
  val apiSubscriptionFieldsHost = "localhost"
  var apiSubscriptionFieldsUrl = s"http://$apiSubscriptionFieldsHost:$apiSubscriptionFieldsPort"
  val apiSubscriptionFieldsServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiSubscriptionFieldsPort))
  var apiSubscriptionFieldsMock : WireMock = _

  override def beforeEach(): Unit = {
    apiSubscriptionFieldsServer.start()
    apiSubscriptionFieldsMock = new WireMock(apiSubscriptionFieldsHost, apiSubscriptionFieldsPort)

    apiScopeServer.start()
    apiScopeMock = new WireMock(apiScopeHost, apiScopePort)

    apiProducerServer.start()
    apiProducerMock = new WireMock(apiProducerHost, apiProducerPort)

    apiDefinitionServer.start()
    apiDefinitionMock = new WireMock(apiDefinitionHost, apiDefinitionPort)

  }

  override def afterEach(): Unit = {
    apiSubscriptionFieldsServer.stop()
    apiScopeServer.stop()
    apiProducerServer.stop()
    apiDefinitionServer.stop()
  }

}
