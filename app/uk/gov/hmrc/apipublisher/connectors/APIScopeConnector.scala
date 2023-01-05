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

import javax.inject.{Inject, Singleton}
import play.api.http.Status.{BAD_REQUEST, UNPROCESSABLE_ENTITY}
import play.api.libs.json.{JsString, JsValue}
import uk.gov.hmrc.apipublisher.models.Scope
import uk.gov.hmrc.apipublisher.util.ApplicationLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UnprocessableEntityException, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class APIScopeConnector @Inject()(config: ApiScopeConfig, http: HttpClient)(implicit val ec: ExecutionContext)
  extends ConnectorRecovery with ApplicationLogger {

  lazy val serviceBaseUrl = config.baseUrl

  def publishScopes(scopes: JsValue)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[JsValue, Either[UpstreamErrorResponse, HttpResponse]](s"$serviceBaseUrl/scope", scopes).map {
      case Right(_) => (())
      case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => throw new UnprocessableEntityException(message)
      case Left(err) => throw err
    }
  }

  def validateScopes(scopes: JsValue)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    val url = s"$serviceBaseUrl/scope/validate"
    http.POST[JsValue, Either[UpstreamErrorResponse, HttpResponse]](url, scopes)
      .map {
        case Right(_) => None
        case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
          logger.debug(s"Failed request. POST url=$url: $message")
          Some(JsString(message))
        case Left(err) => throw err
      }
  }

  def retrieveScopes(scopeKeys: Set[String])(implicit hc: HeaderCarrier): Future[Seq[Scope]] = {
    val url = url"$serviceBaseUrl/scope?keys=${scopeKeys.mkString(" ")}"
    http.GET[Seq[Scope]](url)
  }
}

case class ApiScopeConfig(baseUrl: String)
