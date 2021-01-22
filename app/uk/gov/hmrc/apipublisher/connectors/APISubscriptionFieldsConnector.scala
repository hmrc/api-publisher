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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.apipublisher.models.{ApiFieldDefinitions, ApiSubscriptionFieldDefinitionsRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.apipublisher.models.FieldDefinition
import play.api.libs.json.JsString
import play.api.libs.json.JsValue

@Singleton
class APISubscriptionFieldsConnector @Inject()(config: ApiSSubscriptionFieldsConfig, http: HttpClient)(implicit val ec: ExecutionContext)
  extends ConnectorRecovery {

  lazy val serviceBaseUrl = config.baseUrl

  def publishFieldDefinitions(apiFieldDefinitions: Seq[ApiFieldDefinitions])(implicit hc: HeaderCarrier): Future[Unit] = {
    val putFutures: Iterable[Future[Unit]] = apiFieldDefinitions.map {
      case ApiFieldDefinitions(apiContext, apiVersion, fieldDefinitions) =>
        val urlEncodedApiContext = URLEncoder.encode(apiContext, StandardCharsets.UTF_8.name)
        val jsonRequestBody = Json.toJson(ApiSubscriptionFieldDefinitionsRequest(fieldDefinitions))
        val putUrl = s"$serviceBaseUrl/definition/context/$urlEncodedApiContext/version/$apiVersion"
        http.PUT(putUrl, jsonRequestBody).map(_ => ()) recover unprocessableRecovery
    }
    Future.sequence(putFutures).map(_ => ())
  }

  def validateFieldDefinitions(fieldDefinitions: Seq[FieldDefinition])(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    import uk.gov.hmrc.http.Upstream4xxResponse
    import play.api.http.Status.{UNPROCESSABLE_ENTITY, BAD_REQUEST}

    if(fieldDefinitions.isEmpty)
      Future.successful(None)
    else {
      val jsonRequestBody = Json.toJson(ApiSubscriptionFieldDefinitionsRequest(fieldDefinitions))
      val putUrl = s"$serviceBaseUrl/validate"

      http.POST(putUrl, jsonRequestBody)
      .map(_ => None)
      .recover {
        case Upstream4xxResponse(_, UNPROCESSABLE_ENTITY, _, _) | Upstream4xxResponse(_, BAD_REQUEST, _, _) => Some(JsString("Field definitions are invalid"))
      }
    }
  }
}

case class ApiSSubscriptionFieldsConfig(baseUrl: String)
