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

package uk.gov.hmrc.apipublisher.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ErrorCode, ServiceLocation}
import uk.gov.hmrc.apipublisher.services.{ApprovalService, PublisherService}
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class PublisherController @Inject()(publisherService: PublisherService, approvalService: ApprovalService)
  extends BaseController {

  val FAILED_TO_PUBLISH = "FAILED_TO_PUBLISH_SERVICE"
  val FAILED_TO_VALIDATE = "FAILED_TO_VALIDATE"
  val FAILED_TO_FETCH_UNAPPROVED_SERVICES = "FAILED_TO_FETCH_UNAPPROVED_SERVICES"
  val FAILED_TO_APPROVE_SERVICES = "FAILED_TO_APPROVE_SERVICES"

  def publish: Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    handleRequest[ServiceLocation](FAILED_TO_PUBLISH) {
      requestBody => publishService(requestBody)
    }
  }

  private def publishService(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Result] = {
    Logger.info(s"Publishing service $serviceLocation")
    publisherService.publishAPIDefinitionAndScopes(serviceLocation).map {
      case true =>
        Logger.info(s"Successfully published API Definition and Scopes for ${serviceLocation.serviceName}")
        NoContent
      case _ =>
        Logger.info(s"Publication awaiting approval for ${serviceLocation.serviceName}")
        Accepted
    } recover recovery(s"$FAILED_TO_PUBLISH ${serviceLocation.serviceName}")
  }

  def validate: Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    handleRequest[ApiAndScopes](FAILED_TO_VALIDATE) { requestBody =>
      publisherService.validateAPIDefinitionAndScopes(requestBody).map {
        case Some(errors) => BadRequest(errors)
        case None => NoContent
      } recover recovery(FAILED_TO_VALIDATE)
    }
  }

  def fetchUnapprovedServices(): Action[AnyContent] = Action.async { implicit request =>
    approvalService.fetchUnapprovedServices().map {
      result => Ok(Json.toJson(result.seq))
    } recover recovery(FAILED_TO_FETCH_UNAPPROVED_SERVICES)
  }

  def fetchServiceSummary(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    approvalService.fetchServiceApproval(serviceName)
      .map(res => Ok(Json.toJson(res)))
      .recover(recovery(FAILED_TO_FETCH_UNAPPROVED_SERVICES))
  }

  def approve(serviceName: String): Action[AnyContent] = Action.async { implicit request => {
      for {
        serviceLocation <- approvalService.approveService(serviceName)
        result <- publishService(serviceLocation)
      } yield result
    } recover recovery(FAILED_TO_APPROVE_SERVICES)
  }

  private def handleRequest[T](prefix: String)(f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => Future.successful(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, JsError.toJson(errs))))
      case Failure(e) =>
        Logger.error(s"$prefix - Unprocessable request received: ${e.getMessage} => ${request.body}")
        Future.successful(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage)))
    }
  }

  private def error(errorCode: ErrorCode.Value, message: JsValueWrapper): JsObject = {
    Json.obj(
      "code" -> errorCode.toString,
      "message" -> message
    )
  }

  private def recovery(prefix: String): PartialFunction[Throwable, Result] = {
    case e: UnprocessableEntityException =>
      Logger.error(s"$prefix - Unprocessable request received: ${e.getMessage}", e)
      UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage))
    case e: UnknownApiServiceException =>
      Logger.warn(s"$prefix - Unknown Service: ${e.getMessage}")
      NotFound
    case e =>
      Logger.error(s"$prefix - An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.UNKNOWN_ERROR, "An unexpected error occurred"))
  }

}
