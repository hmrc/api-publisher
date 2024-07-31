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
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.Status.{BAD_REQUEST, UNPROCESSABLE_ENTITY}
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UnprocessableEntityException, UpstreamErrorResponse}

import uk.gov.hmrc.apipublisher.models.{ApiFieldDefinitions, ApiSubscriptionFieldDefinitionsRequest, FieldDefinition}

@Singleton
class APISubscriptionFieldsConnector @Inject() (config: ApiSSubscriptionFieldsConfig, http: HttpClientV2)(implicit val ec: ExecutionContext)
    extends ConnectorRecovery {

  lazy val serviceBaseUrl = config.baseUrl

  def publishFieldDefinitions(apiFieldDefinitions: Seq[ApiFieldDefinitions])(implicit hc: HeaderCarrier): Future[Unit] = {
    val putFutures: Iterable[Future[Unit]] = apiFieldDefinitions.map {
      case ApiFieldDefinitions(apiContext, apiVersion, fieldDefinitions) =>
        http.put(url"$serviceBaseUrl/definition/context/$apiContext/version/$apiVersion")
          .withBody(
            Json.toJson(
              ApiSubscriptionFieldDefinitionsRequest(fieldDefinitions)
            )
          )
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
          .map {
            case Right(_)                                                         => (())
            case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => throw new UnprocessableEntityException(message)
            case Left(err)                                                        => throw err
          }
    }

    Future.sequence(putFutures).map(_ => ())
  }

  def validateFieldDefinitions(fieldDefinitions: Seq[FieldDefinition])(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    if (fieldDefinitions.isEmpty) {
      Future.successful(None)
    } else {
      http
        .post(url"$serviceBaseUrl/validate")
        .withBody(
          Json.toJson(
            ApiSubscriptionFieldDefinitionsRequest(fieldDefinitions)
          )
        )
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
        .map {
          case Right(_)                                                         => None
          case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => Some(JsString("Field definitions are invalid"))
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _))          => Some(JsString("Field definitions are invalid"))
          case Left(err)                                                        => throw err
        }
    }
  }
}

case class ApiSSubscriptionFieldsConfig(baseUrl: String)
