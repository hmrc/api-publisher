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

package uk.gov.hmrc.apipublisher.wiring

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.Json
import play.api.mvc.Results.BadRequest
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.{ErrorResponse, JsonErrorHandler}
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

class ErrorHandler @Inject() (configuration: Configuration, httpAuditEvent: HttpAuditEvent, auditConnector: AuditConnector, implicit val ec: ExecutionContext)
    extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    super.onClientError(request, statusCode, message).map(defaultResponse =>
      statusCode match {
        case BAD_REQUEST => BadRequest(Json.toJson(ErrorResponse(BAD_REQUEST, message)))
        case _           => defaultResponse
      }
    )
  }
}
