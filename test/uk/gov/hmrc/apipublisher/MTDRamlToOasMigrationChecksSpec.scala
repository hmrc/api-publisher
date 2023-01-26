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

import akka.actor.ActorSystem
import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apipublisher.connectors.{DocumentationRamlLoader, DocumentationUrlRewriter, MicroserviceConnector}
import uk.gov.hmrc.apipublisher.models.ServiceLocation
import uk.gov.hmrc.apipublisher.services.OasParserImpl
import uk.gov.hmrc.apipublisher.wiring.AppContext
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.ramltools.domain.Endpoints
import uk.gov.hmrc.ramltools.loaders.UrlRewritingRamlLoader
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MTDRamlToOasMigrationChecksSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {

  SharedMetricRegistries.clear()
  val apiProducerPort = sys.env.getOrElse("WIREMOCK", "21112").toInt
  val apiProducerHost = "127.0.0.1"
  val apiProducerUrl  = s"http://$apiProducerHost:$apiProducerPort"
  val wireMockServer  = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiProducerPort))

  val testService = ServiceLocation("test.example.com", "http://localhost:7793")

  trait BaseSetup {
    WireMock.reset()
    val mockRamlLoader  = mock[DocumentationRamlLoader]
    implicit val hc     = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    implicit val system = app.injector.instanceOf[ActorSystem]

    def oasFileLocator: MicroserviceConnector.OASFileLocator
    def oasParser: SwaggerParserExtension

    lazy val connector = new MicroserviceConnector(
      MicroserviceConnector.Config(validateApiDefinition = true, oasParserMaxDuration = 3.seconds),
      mockRamlLoader,
      oasFileLocator,
      oasParser,
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Environment]
    )
  }

  trait Setup extends BaseSetup {
    val oasFileLocator = mock[MicroserviceConnector.OASFileLocator]
    val oasParser      = new OpenAPIV3Parser()
  }

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(apiProducerHost, apiProducerPort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  "Check OAS and RAML specs are in sync" should {

    "Load both RAML and OAS specs and make sure they are in sync " in new Setup {
      val context = Some("individuals/state-benefits")
      val port = "7789"
      val version = "1.0"
      val mockConfiguration = mock[Configuration]
      val mockEnvironment = mock[Environment]
      val mockServicesConfig = mock[ServicesConfig]

      when(mockConfiguration.getOptional[String]("ramlLoaderUrlRewrite.from")).thenReturn(Option("mockFrom"))
      when(mockConfiguration.getOptional[String]("ramlLoaderUrlRewrite.to")).thenReturn(Option("moTo"))

      val appContext = new AppContext(mockConfiguration, mockEnvironment, mockServicesConfig)
      val urlWriter = new DocumentationUrlRewriter(appContext)
      val ramlLoader = new UrlRewritingRamlLoader(urlWriter)

      when(mockRamlLoader.load(*)).thenReturn(ramlLoader.load(s"http://localhost:$port/api/conf/$version/application.raml"))

      val parser = new OasParserImpl()
      val ramlEndPoints = connector.getRaml(ServiceLocation("localhost", s"http://localhost:$port"), version).map(raml => Endpoints(raml, context).toList).get

      when(oasFileLocator.locationOf(*, *)).thenReturn(s"http://localhost:$port/api/conf/$version/application.yaml")
      val oasEndpoints = await(connector.getOAS(testService, version).map(parser.apply(context)(_)))

      val mismatch = ramlEndPoints.filterNot(e => oasEndpoints.contains(e))
      if (mismatch.isEmpty) {
        println("Both RAML and OAS Specs match and no mismatch found \n")
      } else {
        println("Mismatch found, list of RAML endpoints which do not match in OAS: \n=========")
        mismatch.foreach(println)
      }

      println("\nRAML Endpoints: \n========= ")
      ramlEndPoints.foreach(println)

      println("\nOAS Endpoints: \n=========")
      oasEndpoints.foreach(println)


      ramlEndPoints.toSet shouldBe oasEndpoints.toSet
      ok("Done")
    }

  }
}
