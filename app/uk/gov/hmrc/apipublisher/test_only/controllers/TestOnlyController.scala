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

package uk.gov.hmrc.apipublisher.test_only.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import org.everit.json.schema.ValidationException

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.http.UnprocessableEntityException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.services.ApprovalService
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

@Singleton
class TestOnlyController @Inject() (
    approvalService: ApprovalService,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  private val FAILED_TO_DELETE_API_APPROVAL = "FAILED_TO_DELETE_API_APPROVAL"

  def deleteApiApproval(serviceName: String): Action[AnyContent] = Action.async { _ =>
    approvalService.deleteApiApproval(serviceName).map { _ => NoContent } recover recovery(FAILED_TO_DELETE_API_APPROVAL)
  }

  private def error(errorCode: ErrorCode.Value, message: JsValueWrapper): JsObject = {
    Json.obj(
      "code"    -> errorCode.toString,
      "message" -> message
    )
  }

  private def recovery(prefix: String): PartialFunction[Throwable, Result] = {
    case e: ValidationException          =>
      logger.error(s"$prefix - Validation of API definition failed: ${e.toJSON.toString(2)}", e)
      UnprocessableEntity(error(ErrorCode.INVALID_API_DEFINITION, Json.parse(e.toJSON.toString)))
    case e: UnprocessableEntityException =>
      logger.error(s"$prefix - Unprocessable request received: ${e.getMessage}", e)
      UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage))
    case e: UnknownApiServiceException   =>
      logger.warn(s"$prefix - Unknown Service: ${e.getMessage}")
      NotFound
    case e                               =>
      logger.error(s"$prefix - An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.UNKNOWN_ERROR, s"An unexpected error occurred: ${e.getMessage}"))
  }

}
