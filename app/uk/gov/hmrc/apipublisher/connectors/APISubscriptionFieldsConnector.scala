/*
 * Copyright 2021 HM Revenue & Customs
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

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import uk.gov.hmrc.http.HttpReads.Implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apipublisher.models.{ApiFieldDefinitions, ApiSubscriptionFieldDefinitionsRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.apipublisher.models.FieldDefinition
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.http.Status.{BAD_REQUEST, UNPROCESSABLE_ENTITY}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UnprocessableEntityException

@Singleton
class APISubscriptionFieldsConnector @Inject()(config: ApiSSubscriptionFieldsConfig, http: HttpClient)(implicit val ec: ExecutionContext)
  extends ConnectorRecovery {

  lazy val serviceBaseUrl = config.baseUrl

  def publishFieldDefinitions(apiFieldDefinitions: Seq[ApiFieldDefinitions])(implicit hc: HeaderCarrier): Future[Unit] = {
    val putFutures: Iterable[Future[Unit]] = apiFieldDefinitions.map {
      case ApiFieldDefinitions(apiContext, apiVersion, fieldDefinitions) =>
        val urlEncodedApiContext = URLEncoder.encode(apiContext, StandardCharsets.UTF_8.name)
        val request = ApiSubscriptionFieldDefinitionsRequest(fieldDefinitions)
        val putUrl = s"$serviceBaseUrl/definition/context/$urlEncodedApiContext/version/$apiVersion"
        http.PUT[ApiSubscriptionFieldDefinitionsRequest, Either[UpstreamErrorResponse, HttpResponse]](putUrl, request)
        .map {
          case Right(_) => (())
          case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => throw new UnprocessableEntityException(message)
          case Left(err) => throw err
        }
    }
    
    Future.sequence(putFutures).map(_ => ())
  }

  def validateFieldDefinitions(fieldDefinitions: Seq[FieldDefinition])(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    if(fieldDefinitions.isEmpty)
      Future.successful(None)
    else {
      val request = ApiSubscriptionFieldDefinitionsRequest(fieldDefinitions)
      val putUrl = s"$serviceBaseUrl/validate"

      http.POST[ApiSubscriptionFieldDefinitionsRequest, Either[UpstreamErrorResponse, HttpResponse]](putUrl, request)
        .map {
          case Right(_) => None
          case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => Some(JsString("Field definitions are invalid"))
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _))          => Some(JsString("Field definitions are invalid"))
          case Left(err) => throw err
        }
    }
  }
}

case class ApiSSubscriptionFieldsConfig(baseUrl: String)
