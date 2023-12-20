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

import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import io.swagger.v3.oas.models.OpenAPI
import org.apache.commons.io.IOUtils
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import play.api.Environment
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpReadsOption, HttpResponse}
import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.loaders.RamlLoader
import uk.gov.hmrc.apipublisher.models.APICategory.{OTHER, categoryMap}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, DefinitionFileFailedSchemaValidation, DefinitionFileNoBodyReturned, DefinitionFileNotFound, DefinitionFileUnprocessableEntity, PublishError, ServiceLocation}
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

object MicroserviceConnector {
  case class Config(validateApiDefinition: Boolean, oasParserMaxDuration: FiniteDuration)
}

@Singleton
class MicroserviceConnector @Inject() (
    config: MicroserviceConnector.Config,
    ramlLoader: RamlLoader,
    oasFileLoader: OASFileLoader,
    http: HttpClient,
    env: Environment
  )(implicit val ec: ExecutionContext
  ) extends ConnectorRecovery with HttpReadsOption with ApplicationLogger {

  val apiDefinitionSchema: Schema = {
    val inputStream: InputStream = env.resourceAsStream("api-definition-schema.json").get
    val schema: Schema           = SchemaLoader.load(new JSONObject(IOUtils.toString(inputStream, UTF_8)))
    IOUtils.closeQuietly(inputStream)
    schema
  }

  // Overridden so we can map only 204 to None, rather than also including 404
  implicit override def readOptionOfNotFound[P](implicit rds: HttpReads[P]): HttpReads[Option[P]] = new HttpReads[Option[P]] {

    def read(method: String, url: String, response: HttpResponse): Option[P] = response.status match {
      case NO_CONTENT => None
      case _          => Some(rds.read(method, url, response))
    }
  }

  def getAPIAndScopes(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Either[PublishError, ApiAndScopes]] = {
    import play.api.http.Status.{NOT_FOUND, UNPROCESSABLE_ENTITY}

    import uk.gov.hmrc.http.UpstreamErrorResponse

    val url = s"${serviceLocation.serviceUrl}/api/definition"

    http.GET[Option[ApiAndScopes]](url)(readOptionOfNotFound, implicitly, implicitly)
      .map {
        _.toRight(DefinitionFileNoBodyReturned(s"Unable to find definition for service ${serviceLocation.serviceName}"))
      }
      .recover {
        case UpstreamErrorResponse(message, NOT_FOUND, _, _)            => Left(DefinitionFileNotFound(s"Unable to find definition for service ${serviceLocation.serviceName}"))
        case UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _) => Left(DefinitionFileUnprocessableEntity(message))
      }
      .map(_.map(defaultCategories))
      .map(_.flatMap(validateApiAndScopesAgainstSchema))
  }

  private def validateApiAndScopesAgainstSchema(apiAndScopes: ApiAndScopes): Either[PublishError, ApiAndScopes] = {
    if (config.validateApiDefinition) {
      Try(apiDefinitionSchema.validate(new JSONObject(Json.toJson(apiAndScopes).toString))) match {
        case Success(_)  => Right(apiAndScopes)
        case Failure(ex) => Left(DefinitionFileFailedSchemaValidation(ex.getMessage)) // FailValidationFail
      }
    } else {
      Right(apiAndScopes)
    }
  }

  private def defaultCategories(apiAndScopes: ApiAndScopes): ApiAndScopes = {
    if (apiAndScopes.categories.isEmpty) {
      val defaultCategories = categoryMap.getOrElse(apiAndScopes.apiName, Seq(OTHER))
      val updatedApi        = apiAndScopes.api ++ Json.obj("categories" -> defaultCategories)
      apiAndScopes.copy(api = updatedApi)
    } else {
      apiAndScopes
    }
  }

  def getRaml(serviceLocation: ServiceLocation, version: String): Try[RAML] = {
    ramlLoader.load(s"${serviceLocation.serviceUrl}/api/conf/$version/application.raml")
  }

  def getOAS(serviceLocation: ServiceLocation, version: String): Future[OpenAPI] = {
    oasFileLoader.load(serviceLocation, version, config.oasParserMaxDuration)
  }

}
