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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.http.Status.{BAD_REQUEST, UNPROCESSABLE_ENTITY}
import uk.gov.hmrc.apipublisher.util.ApplicationLogger
import uk.gov.hmrc.http.UnprocessableEntityException

@Singleton
class APIDefinitionConnector @Inject() (config: ApiDefinitionConfig, http: HttpClient)(implicit val ec: ExecutionContext)
    extends ConnectorRecovery with ApplicationLogger {

  lazy val serviceBaseUrl = config.baseUrl

  def publishAPI(api: JsObject)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](s"$serviceBaseUrl/api-definition", api, Seq.empty).map {
      case Right(_)                                                         => (())
      case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => throw new UnprocessableEntityException(message)
      case Left(err)                                                        => throw err
    }
  }

  def validateAPIDefinition(definition: JsObject)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    val url = s"$serviceBaseUrl/api-definition/validate"
    http.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, definition ++ Json.obj("serviceBaseUrl" -> "dummy", "serviceName" -> "dummy"))
      .map {
        case Right(_)                                                => None
        case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
          logger.debug(s"Failed request. POST url=$url: $message")
          Some(JsString(message))
        case Left(err)                                               => throw err
      }
  }
}

case class ApiDefinitionConfig(baseUrl: String)
