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

package uk.gov.hmrc.apipublisher.services

import javax.inject.Inject
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import cats.implicits._

import play.api.libs.json._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ramltools.domain.Endpoint

import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

object DefinitionService {

  trait VersionDefinitionService {
    def getDetailForVersion(serviceLocation: ServiceLocation, context: Option[String], version: String): Future[List[Endpoint]]
  }
}

class DefinitionService @Inject() (
    microserviceConnector: MicroserviceConnector,
    ramlVersionDefinitionService: RamlVersionDefinitionService,
    oasVersionDefinitionService: OasVersionDefinitionService
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  val E = EitherTHelper.make[PublishError]

  def getDefinition(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Either[PublishError, ApiAndScopes]] = {
    (
      for {
        baseApiAndScopes     <- E.fromEitherF(microserviceConnector.getAPIAndScopes(serviceLocation))
        detailedApiAndScopes <- E.liftF(addDetailFromSpecification(serviceLocation, baseApiAndScopes))
      } yield detailedApiAndScopes
    )
      .value
  }

  private def addDetailFromSpecification(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes): Future[ApiAndScopes] = {
    val api      = apiAndScopes.api
    val context  = (api \ "context").asOpt[String]
    val versions = (api \ "versions").as[List[JsObject]]

    val fDetailedVersions =
      Future.sequence(
        versions.map { versionObj =>
          val versionNbr = (versionObj \ "version").as[String]
          val details    = getDetailForVersion(serviceLocation, context, versionNbr)

          details.map {
            case (endpoints, source) =>
              versionObj +
                ("endpoints"     -> Json.toJson(endpoints).as[JsArray]) +
                ("versionSource" -> JsString(source.asText))
          }
        }
      )

    fDetailedVersions.map { detailsVersions =>
      apiAndScopes.copy(api = api + ("versions" -> JsArray(detailsVersions)))
    }
  }

  private def getDetailForVersion(serviceLocation: ServiceLocation, context: Option[String], version: String): Future[(List[Endpoint], ApiVersionSource)] = {
    lazy val describeService: String = s"${serviceLocation.serviceName} - v${version}"

    val ramlVD = ramlVersionDefinitionService.getDetailForVersion(serviceLocation, context, version)
      .orElse(successful(List.empty))

    val oasVD: Future[Either[Throwable, List[Endpoint]]] =
      oasVersionDefinitionService.getDetailForVersion(serviceLocation, context, version)
        .map(_.asRight[Throwable])
        .recover {
          case NonFatal(t) => Left(t)
        }

    ramlVD.flatMap { raml =>
      oasVD.map { oas =>
        (raml, oas) match {
          case (Nil, Right(Nil))                                                                   =>
            throw new IllegalStateException(s"No endpoints defined for $version of ${serviceLocation.serviceName}")
          case (Nil, Left(t))                                                                      =>
            throw new IllegalStateException(s"No endpoints defined for $version of ${serviceLocation.serviceName} due to failure in OAS Parsing - [${t.getMessage()}]")
          case (ramlEndpoints, Right(Nil))                                                         =>
            logger.info(s"${describeService} : Using RAML to publish")
            (ramlEndpoints, ApiVersionSource.RAML)
          case (ramlEndpoints, Left(t))                                                            =>
            logger.info(s"${describeService} : Using RAML to publish due to failure in OAS Parsing - [${t.getMessage()}]")
            (ramlEndpoints, ApiVersionSource.RAML)
          case (Nil, Right(oasEndpoints))                                                          => logger.info(s"${describeService} : Using OAS to publish"); (oasEndpoints, ApiVersionSource.OAS)
          case (ramlEndpoints, Right(oasEndpoints)) if (ramlEndpoints.toSet == oasEndpoints.toSet) =>
            logger.info(s"${describeService} : Both RAML and OAS match for publishing")
            (oasEndpoints, ApiVersionSource.OAS)
          case (ramlEndpoints, Right(oasEndpoints))                                                =>
            logger.warn(s"${describeService} : Mismatched RAML <$ramlEndpoints>  OAS <$oasEndpoints>"); (ramlEndpoints, ApiVersionSource.RAML)
        }
      }
    }
  }
}
