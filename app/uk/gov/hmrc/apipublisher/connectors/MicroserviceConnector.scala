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
import org.everit.json.schema.loader.SchemaLoader
import org.everit.json.schema.{Schema, ValidationException}
import org.json.JSONObject

import play.api.Environment
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpReadsOption, HttpResponse, StringContextOps}

import uk.gov.hmrc.apipublisher.models.APICategory.{OTHER, categoryMap}
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

object MicroserviceConnector {
  case class Config(validateApiDefinition: Boolean, oasParserMaxDuration: FiniteDuration)
}

@Singleton
class MicroserviceConnector @Inject() (
    config: MicroserviceConnector.Config,
    oasFileLoader: OASFileLoader,
    http: HttpClientV2,
    env: Environment
  )(implicit val ec: ExecutionContext
  ) extends ConnectorRecovery with HttpReadsOption with ApplicationLogger {

  private val apiDefinitionSchema: Schema = {
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

  def getProducerApiDefinition(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Either[PublishError, ProducerApiDefinition]] = {
    import play.api.http.Status.{NOT_FOUND, UNPROCESSABLE_ENTITY}

    import uk.gov.hmrc.http.UpstreamErrorResponse

    http
      .get(url"${serviceLocation.serviceUrl}/api/definition")
      .execute[Either[UpstreamErrorResponse, Option[JsObject]]] // Uses readOptionOfNotFound for reading
      .map {
        case Right(definition)                                                => definition.toRight(DefinitionFileNoBodyReturned(serviceLocation))
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))                  => Left(DefinitionFileNotFound(serviceLocation))
        case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => Left(DefinitionFileUnprocessableEntity(serviceLocation, message))
        case Left(err)                                                        => throw err
      }
      .map(_.flatMap(validateDefinition))
      .map(_.map(defaultCategories))
  }

  private def validateDefinition(definition: JsObject): Either[PublishError, ProducerApiDefinition] = {
    val producerApiDefinition = ProducerApiDefinition((definition \ "api").as[JsObject])
    if (config.validateApiDefinition) {
      val definitionJsonObj = new JSONObject(definition.toString)
      Try(apiDefinitionSchema.validate(definitionJsonObj)) match {
        case Success(_)                       => Right(producerApiDefinition)
        case Failure(ex: ValidationException) =>
          logger.error(s"FAILED_TO_PUBLISH - Validation of API definition failed: ${ex.toJSON.toString(2)}", ex)
          Left(DefinitionFileFailedSchemaValidation(Json.parse(ex.toJSON.toString)))
        case Failure(ex)                      => Left(DefinitionFileFailedSchemaValidation(Json.parse(s"""{"Unexpected exception": "$ex.message"}""")))
      }
    } else {
      Right(producerApiDefinition)
    }
  }

  private def defaultCategories(producerApiDefinition: ProducerApiDefinition): ProducerApiDefinition = {
    if (producerApiDefinition.categories.isEmpty) {
      val defaultCategories = categoryMap.getOrElse(producerApiDefinition.apiName, Seq(OTHER))
      val updatedApi        = producerApiDefinition.api ++ Json.obj("categories" -> defaultCategories)
      producerApiDefinition.copy(api = updatedApi)
    } else {
      producerApiDefinition
    }
  }

  def getOAS(serviceLocation: ServiceLocation, version: String): Future[OpenAPI] = {
    oasFileLoader.load(serviceLocation, version, config.oasParserMaxDuration)
  }

}
