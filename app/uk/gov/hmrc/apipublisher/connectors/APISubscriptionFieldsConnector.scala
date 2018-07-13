/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.apipublisher.config.WSHttp
import uk.gov.hmrc.apipublisher.models.{ApiFieldDefinitions, ApiSubscriptionFieldDefinitionsRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.inject.DefaultServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class APISubscriptionFieldsConnector @Inject()(servicesConfig: DefaultServicesConfig, http: WSHttp) extends ConnectorRecovery {

  lazy val serviceBaseUrl = servicesConfig.baseUrl("api-subscription-fields")

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

}