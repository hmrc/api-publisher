/*
 * Copyright 2022 HM Revenue & Customs
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

import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import uk.gov.hmrc.http.HttpReads.Implicits._
import javax.inject.{Inject, Singleton}
import org.apache.commons.io.IOUtils
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import play.api.Environment
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import uk.gov.hmrc.apipublisher.models.APICategory.{OTHER, categoryMap}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, HttpReadsOption}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.loaders.RamlLoader

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try
import io.swagger.v3.oas.models.OpenAPI
import scala.collection.JavaConverters._
import io.swagger.v3.parser.core.models.ParseOptions
import uk.gov.hmrc.apipublisher.util.ApplicationLogger
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension
import akka.actor.ActorSystem
import scala.concurrent.duration.FiniteDuration
import java.io.FileNotFoundException

object MicroserviceConnector {
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
class MicroserviceConnector @Inject()(
  config: MicroserviceConnector.Config,
  ramlLoader: RamlLoader,
  oasFileLocator: MicroserviceConnector.OASFileLocator,
  openAPIV3Parser: SwaggerParserExtension,
  http: HttpClient,
  env: Environment
)(implicit val ec: ExecutionContext, system: ActorSystem) extends ConnectorRecovery with HttpReadsOption with ApplicationLogger {

  val apiDefinitionSchema: Schema = {
    val inputStream: InputStream = env.resourceAsStream("api-definition-schema.json").get
    val schema: Schema = SchemaLoader.load(new JSONObject(IOUtils.toString(inputStream, UTF_8)))
    IOUtils.closeQuietly(inputStream)
    schema
  }

  // Overridden so we can map only 204 to None, rather than also including 404
  implicit override def readOptionOfNotFound[P](implicit rds: HttpReads[P]): HttpReads[Option[P]] = new HttpReads[Option[P]] {
    def read(method: String, url: String, response: HttpResponse): Option[P] = response.status match {
      case NO_CONTENT => None
      case _ => Some(rds.read(method, url, response))
    }
  }

  def getAPIAndScopes(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Option[ApiAndScopes]] = {
    val url = s"${serviceLocation.serviceUrl}/api/definition"
    http.GET[Option[ApiAndScopes]](url)(readOptionOfNotFound, implicitly, implicitly)
      .map(validateApiAndScopesAgainstSchema)
      .map(defaultCategories)
      .recover(unprocessableRecovery)
  }

  private def validateApiAndScopesAgainstSchema(apiAndScopes: Option[ApiAndScopes]): Option[ApiAndScopes] = {
    apiAndScopes map { definition =>
      if (config.validateApiDefinition) {
        apiDefinitionSchema.validate(new JSONObject(Json.toJson(definition).toString))
      }
      definition
    }
  }

  private def defaultCategories(apiAndScopes: Option[ApiAndScopes]) = {
    apiAndScopes map { definition =>
      if (definition.categories.isEmpty) {
        val defaultCategories = categoryMap.getOrElse(definition.apiName, Seq(OTHER))
        val updatedApi = definition.api ++ Json.obj("categories" -> defaultCategories)
        definition.copy(api = updatedApi)
      } else {
        definition
      }
    }
  }

  def getRaml(serviceLocation: ServiceLocation, version: String): Try[RAML] = {
    ramlLoader.load(s"${serviceLocation.serviceUrl}/api/conf/$version/application.raml")
  }

  def getOAS(serviceLocation: ServiceLocation, version: String): Future[OpenAPI] = {
    def handleSuccess(openApi: OpenAPI): Future[OpenAPI] = {
      logger.info(s"Read OAS file from ${serviceLocation.serviceUrl}")
      Future.successful(openApi)
    }

    def handleFailure(err: List[String]): Future[OpenAPI] = {
      logger.warn(s"Failed to load OAS file from ${serviceLocation.serviceUrl} due to [${err.mkString}]")
      Future.failed(new IllegalArgumentException("Cannot find valid OAS file"))
    }
    
    val parseOptions = new ParseOptions();
    parseOptions.setResolve(true);
    parseOptions.setResolveFully(true);
    val emptyAuthList = java.util.Collections.emptyList[io.swagger.v3.parser.core.models.AuthorizationValue]()

    val futureParsing = Future {
      blocking {
        try {
          val parserResult = openAPIV3Parser.readLocation(oasFileLocator.locationOf(serviceLocation, version), emptyAuthList, parseOptions)
        
          val outcome = (Option(parserResult.getMessages), Option(parserResult.getOpenAPI)) match {
            case (Some(msgs), _) if msgs.size > 0 => Left(msgs.asScala.toList)
            case (_, Some(openApi)) => Right(openApi)
            case _ => Left(List("No errors or openAPI were returned from parsing"))
          }

          outcome
        } catch {
          case e: FileNotFoundException => Left(List(e.getMessage()))
        }
      }
    }
    .flatMap( outcome => 
      outcome.fold( 
          err => handleFailure(err),
          oasApi => handleSuccess(oasApi)
      )
    )

    val futureTimer: Future[OpenAPI] = akka.pattern.after(config.oasParserMaxDuration, using = system.scheduler)(Future.failed(new IllegalStateException("Exceeded OAS parse time")))

    Future.firstCompletedOf(List(futureParsing, futureTimer))
  }
}
