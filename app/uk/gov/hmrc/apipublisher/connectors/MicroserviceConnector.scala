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
import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}

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
import io.swagger.v3.oas.models.OpenAPI

import uk.gov.hmrc.apipublisher.models.APICategory.{OTHER, categoryMap}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

object MicroserviceConnector {
  case class Config(validateApiDefinition: Boolean, oasParserMaxDuration: FiniteDuration)
}

@Singleton
class MicroserviceConnector @Inject() (
    config: MicroserviceConnector.Config,
    ramlLoader: RamlLoader,
    oasFileLoader: OASfileLoader,
    http: HttpClient,
    env: Environment
  )(implicit val ec: ExecutionContext) extends ConnectorRecovery with HttpReadsOption with ApplicationLogger {

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
        val updatedApi        = definition.api ++ Json.obj("categories" -> defaultCategories)
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
    oasFileLoader.load(serviceLocation, version, config.oasParserMaxDuration)
   }
 
}
