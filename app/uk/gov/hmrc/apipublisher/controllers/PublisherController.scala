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

package uk.gov.hmrc.apipublisher.controllers

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import org.everit.json.schema.ValidationException

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ErrorCode, ServiceLocation}
import uk.gov.hmrc.apipublisher.services.{ApprovalService, DefinitionService, PublisherService}
import uk.gov.hmrc.apipublisher.util.ApplicationLogger
import uk.gov.hmrc.apipublisher.wiring.AppContext

@Singleton
class PublisherController @Inject() (
    definitionService: DefinitionService,
    publisherService: PublisherService,
    approvalService: ApprovalService,
    appContext: AppContext,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  val FAILED_TO_PUBLISH                   = "FAILED_TO_PUBLISH_SERVICE"
  val FAILED_TO_VALIDATE                  = "FAILED_TO_VALIDATE"
  val FAILED_TO_FETCH_UNAPPROVED_SERVICES = "FAILED_TO_FETCH_UNAPPROVED_SERVICES"
  val FAILED_TO_APPROVE_SERVICES          = "FAILED_TO_APPROVE_SERVICES"

  def publish: Action[JsValue] = Action.async(controllerComponents.parsers.json) { implicit request =>
    handleRequest[ServiceLocation](FAILED_TO_PUBLISH) {
      requestBody => publishService(requestBody)
    }
  }

  private def publishService(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Result] = {
    logger.info(s"Publishing service $serviceLocation")

    import cats.data.{OptionT, EitherT}
    import cats.implicits._

    type Over[A] = EitherT[Future, Result, A]

    def getDefinition: Over[ApiAndScopes] = {
      EitherT(definitionService.getDefinition(serviceLocation).map(_.toRight(BadRequest)))
    }

    def validate(apiAndScopes: ApiAndScopes): Over[Unit] = {
      EitherT.fromOptionF(
        OptionT(publisherService.validation(apiAndScopes, validateApiDefinition = false)).map(BadRequest(_)).value,
        ()
      ).swap
    }

    def publish(apiAndScopes: ApiAndScopes): Future[Result] = {
      publisherService.publishAPIDefinitionAndScopes(serviceLocation, apiAndScopes).map {
        case true  =>
          logger.info(s"Successfully published API Definition and Scopes for ${serviceLocation.serviceName}")
          NoContent
        case false =>
          logger.info(s"Publication awaiting approval for ${serviceLocation.serviceName}")
          Accepted
      }
    }

    getDefinition.flatMap(apiAndScopes => {
      validate(apiAndScopes).semiflatMap(_ =>
        publish(apiAndScopes)
      )
    }).merge.recover(recovery(s"$FAILED_TO_PUBLISH ${serviceLocation.serviceName}"))
  }

  def validate: Action[JsValue] = Action.async(controllerComponents.parsers.json) { implicit request =>
    handleRequest[ApiAndScopes](FAILED_TO_VALIDATE) { requestBody =>
      publisherService.validateAPIDefinitionAndScopes(requestBody).map {
        case Some(errors) => BadRequest(errors)
        case None         => NoContent
      } recover recovery(FAILED_TO_VALIDATE)
    }
  }

  def fetchUnapprovedServices(): Action[AnyContent] = Action.async { _ =>
    approvalService.fetchUnapprovedServices().map {
      result => Ok(Json.toJson(result.seq))
    } recover recovery(FAILED_TO_FETCH_UNAPPROVED_SERVICES)
  }

  def fetchServiceSummary(serviceName: String): Action[AnyContent] = Action.async { _ =>
    approvalService.fetchServiceApproval(serviceName)
      .map(res => Ok(Json.toJson(res)))
      .recover(recovery(FAILED_TO_FETCH_UNAPPROVED_SERVICES))
  }

  def approve(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    ({
      for {
        serviceLocation <- approvalService.approveService(serviceName)
        result          <- publishService(serviceLocation)
      } yield result
    }) recover recovery(FAILED_TO_APPROVE_SERVICES)
  }

  private def handleRequest[T](prefix: String)(f: T => Future[Result])(implicit request: Request[JsValue], reads: Reads[T]): Future[Result] = {
    val authHeader = request.headers.get("Authorization")
    if (authHeader.isEmpty || appContext.publishingKey != base64Decode(authHeader.get)) {
      Future.successful(Unauthorized(error(ErrorCode.UNAUTHORIZED, "Agent must be authorised to perform Publish or Validate actions")))
    } else {
      Try(request.body.validate[T]) match {
        case Success(JsSuccess(payload, _)) => f(payload)
        case Success(JsError(errs))         => Future.successful(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, JsError.toJson(errs))))
        case Failure(e)                     =>
          logger.error(s"$prefix - Unprocessable request received: ${e.getMessage} => ${request.body}")
          Future.successful(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage)))
      }
    }
  }

  private def base64Decode(stringToDecode: String): String = {
    new String(Base64.getDecoder.decode(stringToDecode), StandardCharsets.UTF_8)
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
