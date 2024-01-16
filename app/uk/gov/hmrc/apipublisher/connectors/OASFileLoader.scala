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
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters._

import akka.actor.ActorSystem
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension
import io.swagger.v3.parser.core.models.ParseOptions

import uk.gov.hmrc.apipublisher.models.ServiceLocation
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

object OASFileLoader {

  trait OASFileLocator {
    def locationOf(serviceLocation: ServiceLocation, version: String): String
  }

  object MicroserviceOASFileLocator extends OASFileLocator {

    def locationOf(serviceLocation: ServiceLocation, version: String): String =
      s"${serviceLocation.serviceUrl}/api/conf/$version/application.yaml"
  }

  case class Config(validateApiDefinition: Boolean, oasParserMaxDuration: FiniteDuration)
}

@Singleton
class OASFileLoader @Inject() (oasFileLocator: OASFileLoader.OASFileLocator, openAPIV3Parser: SwaggerParserExtension)(implicit val ec: ExecutionContext, system: ActorSystem)
    extends ApplicationLogger {

  def load(serviceLocation: ServiceLocation, version: String, oasParserMaxDuration: FiniteDuration): Future[OpenAPI] = {
    def handleSuccess(openApi: OpenAPI): Future[OpenAPI] = {
      logger.info(s"Read OAS file from ${serviceLocation.serviceUrl}")
      Future.successful(openApi)
    }

    def handleFailure(err: List[String]): Future[OpenAPI] = {
      logger.warn(s"Failed to load OAS file from ${serviceLocation.serviceUrl} due to [${err.mkString}]")
      Future.failed(new IllegalArgumentException("Cannot find valid OAS file"))
    }

    val parseOptions  = new ParseOptions();
    parseOptions.setResolve(true);
    parseOptions.setResolveFully(true);
    val emptyAuthList = java.util.Collections.emptyList[io.swagger.v3.parser.core.models.AuthorizationValue]()

    val futureParsing = Future {
      blocking {
        try {
          val parserResult = openAPIV3Parser.readLocation(oasFileLocator.locationOf(serviceLocation, version), emptyAuthList, parseOptions)

          val outcome = (Option(parserResult.getMessages), Option(parserResult.getOpenAPI)) match {
            case (Some(msgs), _) if msgs.size > 0 => Left(msgs.asScala.toList)
            case (_, Some(openApi))               => Right(openApi)
            case _                                => Left(List("No errors or openAPI were returned from parsing"))
          }

          outcome
        } catch {
          case e: FileNotFoundException => Left(List(e.getMessage()))
        }
      }
    }
      .flatMap(outcome =>
        outcome.fold(
          err => handleFailure(err),
          oasApi => handleSuccess(oasApi)
        )
      )

    val futureTimer: Future[OpenAPI] = akka.pattern.after(oasParserMaxDuration, using = system.scheduler)(Future.failed(new IllegalStateException("Exceeded OAS parse time")))

    Future.firstCompletedOf(List(futureParsing, futureTimer))
  }
}
