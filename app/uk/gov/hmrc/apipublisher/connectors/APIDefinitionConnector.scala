/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import uk.gov.hmrc.apipublisher.config.WSHttp
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.config.inject.DefaultServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class APIDefinitionConnector @Inject()(servicesConfig: DefaultServicesConfig, http: WSHttp) extends ConnectorRecovery {

  lazy val serviceBaseUrl = servicesConfig.baseUrl("api-definition")

  def publishAPI(api: JsObject)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(s"$serviceBaseUrl/api-definition", api).map(_ => ()) recover unprocessableRecovery
  }

  def validateAPIDefinition(definition: JsObject)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    val url = s"$serviceBaseUrl/api-definition/validate"
    http.POST(url, definition ++ Json.obj("serviceBaseUrl" -> "dummy", "serviceName" -> "dummy"))
      .map(_ => None)
      .recover {
        case e: BadRequestException =>
          Logger.debug(s"Failed request. POST url=$url: ${e.message}")
          Some(JsString(e.message))
      }
  }
}
