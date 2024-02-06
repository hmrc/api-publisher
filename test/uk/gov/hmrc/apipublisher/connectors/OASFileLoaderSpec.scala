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

package uk.gov.hmrc.apipublisher.connectors

import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import java.{util => ju}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import com.github.tomakehurst.wiremock.client.WireMock._
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension
import io.swagger.v3.parser.core.models.{AuthorizationValue, ParseOptions, SwaggerParseResult}
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils.AsyncHmrcSpec

import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames.xRequestId

import uk.gov.hmrc.apipublisher.models.ServiceLocation

class OASFileLoaderSpec extends AsyncHmrcSpec with BeforeAndAfterAll with GuiceOneAppPerSuite {

  trait BaseSetup {

    implicit val hc: HeaderCarrier   = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")
    implicit val system: ActorSystem = app.injector.instanceOf[ActorSystem]

    def oasFileLocator: OASFileLoader.OASFileLocator
    def oasParser: SwaggerParserExtension

    val appConfig: Configuration = mock[Configuration]

    val oasParserMaxDuration = FiniteDuration.apply(20000, TimeUnit.MILLISECONDS)
    lazy val oasFileLoader   = new OASFileLoader(oasFileLocator, oasParser)
    val serviceLocation      = ServiceLocation("my-service", "it-doesnt-matter", None)
  }

  trait Setup extends BaseSetup {
    val oasFileLocator = mock[OASFileLoader.OASFileLocator]
    val oasParser      = new OpenAPIV3Parser()
  }

  trait SetupWithTimedOutParser extends BaseSetup {
    val oasFileLocator = mock[OASFileLoader.OASFileLocator]

    val oasParser = new SwaggerParserExtension {

      override def readLocation(x$1: String, x$2: ju.List[AuthorizationValue], x$3: ParseOptions): SwaggerParseResult = {
        Thread.sleep(15000)
        throw new RuntimeException("Should have crashed out of the blocking by now")
      }

      override def readContents(x$1: String, x$2: ju.List[AuthorizationValue], x$3: ParseOptions): SwaggerParseResult = ???

    }
  }

  trait SetupWithMockedOpenApiParser extends Setup {
    val mockOpenApiParser = mock[OpenAPIV3Parser]

    override lazy val oasFileLoader = new OASFileLoader(oasFileLocator, mockOpenApiParser)
  }

  "getOAS" should {
    "load the OAS file when found and is a valid model" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/application.yaml")

      await(oasFileLoader.load(serviceLocation, "1.0", oasParserMaxDuration))

      ok("Done")
    }

    "load the OAS file when multifile OAS is found and is a valid model" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/multifile/v1/application.yaml")

      await(oasFileLoader.load(serviceLocation, "1.0", oasParserMaxDuration))

      ok("Done")
    }

    "handle an invalid OAS file" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/bad-application.yaml")

      intercept[RuntimeException] {
        await(oasFileLoader.load(serviceLocation, "1.0", oasParserMaxDuration))
      }
    }

    "handle when the OAS file is not found" in new Setup {
      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/no-such-application.yaml")

      intercept[RuntimeException] {
        await(oasFileLoader.load(serviceLocation, "1.0", oasParserMaxDuration))
      }
    }

    "handle a FileNotFoundException when locating yaml specification" in new SetupWithMockedOpenApiParser {
      when(mockOpenApiParser.readLocation(*, *, *)).thenThrow(new FileNotFoundException("A problem reading the YAML file"))

      intercept[IllegalArgumentException] {
        await(oasFileLoader.load(serviceLocation, "1.0", oasParserMaxDuration))
      }.getMessage() shouldBe "Cannot find valid OAS file"
    }

    // Flakey test in build server...
    "return timeout when OAS parser takes too long" ignore new SetupWithTimedOutParser {
      import scala.concurrent.duration._

      when(oasFileLocator.locationOf(*, *)).thenReturn("/input/oas/no-such-application.yaml")

      intercept[IllegalStateException] {
        Await.result(oasFileLoader.load(serviceLocation, "1.0", oasParserMaxDuration), 29.seconds)
      }
    }
  }
}
