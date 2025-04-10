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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import org.scalatestplus.play.guice.{GuiceOneAppPerSuite, GuiceOneServerPerSuite}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.RunningServer
import sttp.client3.{Request, Response, SimpleHttpClient}
import uk.gov.hmrc.apipublisher.models.APIApproval
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

abstract class BaseFeatureSpec extends AnyFeatureSpec
    with GivenWhenThen with ScalaFutures
    with BeforeAndAfterEach with Matchers with GuiceOneServerPerSuite
  with DefaultPlayMongoRepositorySupport[APIApproval]
  {

//  val appPort      = 12121
  val serverUrl = s"http://localhost:$port"
  val encodedPublishingKey: String = new String(Base64.getEncoder.encode(app.configuration.get[String]("publishingKey").getBytes), StandardCharsets.UTF_8)

    override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(Map(
      "mongodb.uri" -> mongoUri,
      "publishingKey" -> UUID.randomUUID().toString
    ))
    .build()

    override protected val repository: PlayMongoRepository[APIApproval] = app.injector.instanceOf[APIApprovalRepository]

//    val serverUrl = app.

  val apiDefinitionPort: Int      = sys.env.getOrElse("WIREMOCK", "9604").toInt
  val apiDefinitionHost           = "localhost"
  var apiDefinitionUrl            = s"http://$apiDefinitionHost:$apiDefinitionPort"
  val apiDefinitionServer         = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDefinitionPort))
  var apiDefinitionMock: WireMock = _

  val apiProducerPort: Int      = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiProducerHost           = "127.0.0.1"
  val apiProducerUrl            = s"http://$apiProducerHost:$apiProducerPort"
  val apiProducerServer         = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiProducerPort))
  var apiProducerMock: WireMock = _

  val apiSubscriptionFieldsPort: Int      = sys.env.getOrElse("WIREMOCK", "9650").toInt
  val apiSubscriptionFieldsHost           = "localhost"
  var apiSubscriptionFieldsUrl            = s"http://$apiSubscriptionFieldsHost:$apiSubscriptionFieldsPort"
  val apiSubscriptionFieldsServer         = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiSubscriptionFieldsPort))
  var apiSubscriptionFieldsMock: WireMock = _

  val tpaPort: Int      = sys.env.getOrElse("WIREMOCK", "9607").toInt
  val tpaHost           = "localhost"
  var tpaUrl            = s"http://$tpaHost:$tpaPort"
  val tpaServer         = new WireMockServer(WireMockConfiguration.wireMockConfig().port(tpaPort))
  var tpaMock: WireMock = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    apiSubscriptionFieldsServer.start()
    apiSubscriptionFieldsMock = new WireMock(apiSubscriptionFieldsHost, apiSubscriptionFieldsPort)

    apiProducerServer.start()
    apiProducerMock = new WireMock(apiProducerHost, apiProducerPort)

    apiDefinitionServer.start()
    apiDefinitionMock = new WireMock(apiDefinitionHost, apiDefinitionPort)

    tpaServer.start()
    tpaMock = new WireMock(tpaHost, tpaPort)

  }

  override def afterEach(): Unit = {
    super.afterEach()
    apiSubscriptionFieldsServer.stop()
    apiProducerServer.stop()
    apiDefinitionServer.stop()
    tpaServer.stop()
  }

  def http(request: => Request[Either[String, String], Any]): Response[Either[String, String]] = {
    val httpClient = SimpleHttpClient()
    val response   = httpClient.send(request)
    httpClient.close()
    response
  }
}
