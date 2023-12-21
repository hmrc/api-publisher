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

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import org.everit.json.schema.ValidationException
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apipublisher.config.AppConfig
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.services.{ApprovalService, DefinitionService, PublisherService}
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

@Singleton
class PublisherController @Inject() (
    definitionService: DefinitionService,
    publisherService: PublisherService,
    approvalService: ApprovalService,
    appConfig: AppConfig,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  val FAILED_TO_PUBLISH                   = "FAILED_TO_PUBLISH_SERVICE"
  val FAILED_TO_VALIDATE                  = "FAILED_TO_VALIDATE"
  val FAILED_TO_FETCH_UNAPPROVED_SERVICES = "FAILED_TO_FETCH_UNAPPROVED_SERVICES"
  val FAILED_TO_APPROVE_SERVICES          = "FAILED_TO_APPROVE_SERVICES"

  val ER = EitherTHelper.make[Result]

  private val mapBusinessErrorsToResults: PublishError => Result = _ match {
    case err : DefinitionFileNotFound                          => BadRequest(error(ErrorCode.INVALID_API_DEFINITION, err.message))
    case err : DefinitionFileNoBodyReturned                    => BadRequest(error(ErrorCode.INVALID_API_DEFINITION, err.message))
    case err : DefinitionFileUnprocessableEntity               => UnprocessableEntity(error(ErrorCode.INVALID_API_DEFINITION, err.message))
    case DefinitionFileFailedSchemaValidation(message: String) => UnprocessableEntity(error(ErrorCode.INVALID_API_DEFINITION, message))
    case err: GenericValidationFailure                         => UnprocessableEntity(error(ErrorCode.INVALID_API_DEFINITION, err.message))
  }
  
  private def ensureAuthorised(implicit request: Request[JsValue]): Option[Result] = {
    lazy val failedResult = Some(Unauthorized(error(ErrorCode.UNAUTHORIZED, "Agent must be authorised to perform Publish or Validate actions")) )
    request.headers.get("Authorization") match {
      case None                                                            => failedResult
      case Some(value) if (appConfig.publishingKey != base64Decode(value)) => failedResult
      case _                                                               => None
    }
  }

  private def validateRequestPayload[T](implicit request: Request[JsValue], reads: Reads[T]): Either[Result, T] = {
    request.body.validate[T] match {
      case JsSuccess(payload, _) => Right(payload)
      case err : JsError         => Left(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, s"Unable to parse request body : ${JsError.toJson(err)}")))
    }
  }

  def publish: Action[JsValue] = Action.async(controllerComponents.parsers.json) { implicit request =>
    def innerPublish(serviceLocation: ServiceLocation): Future[Result] = {
      ER.liftF(publishService(serviceLocation))
      .merge
      .recover(recovery(s"$FAILED_TO_PUBLISH ${serviceLocation.serviceName}"))
    }

    val serviceLocationER =
      (
        for {
          _               <- ER.fromEither(ensureAuthorised.toRight(()).swap)
          serviceLocation <- ER.fromEither(validateRequestPayload[ServiceLocation])
        } yield serviceLocation
      )

    serviceLocationER.semiflatMap(innerPublish(_))
    .merge
  }

  private def publishService(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Result] = {
    logger.info(s"Publishing service $serviceLocation")

    import cats.implicits._
    val E = EitherTHelper.make[PublishError]

    def validateApiAndScopes(apiAndScopes: ApiAndScopes): Future[Either[PublishError, ApiAndScopes]] = {
      publisherService.validation(apiAndScopes, validateApiDefinition = false).map(_.toRight(apiAndScopes).map(GenericValidationFailure(_)).swap)
    }

    def publishApiAndScopes(apiAndScopes: ApiAndScopes): Future[Result] = {
      publisherService.publishAPIDefinitionAndScopes(serviceLocation, apiAndScopes).map {
        case PublicationResult(true, publisherResponse)  =>
          logger.info(s"Successfully published API Definition and Scopes for ${serviceLocation.serviceName}")
          Ok(Json.toJson(publisherResponse))
        case PublicationResult(false, publisherResponse) =>
          logger.info(s"Publication awaiting approval for ${serviceLocation.serviceName}")
          Accepted(Json.toJson(publisherResponse))
      }
    }

    (
      for {
        apiAndScopes          <- E.fromEitherF(definitionService.getDefinition(serviceLocation))
        validatedApiAndScopes <- E.fromEitherF(validateApiAndScopes(apiAndScopes))
        publisherResponse     <- E.liftF(publishApiAndScopes(apiAndScopes))
      } yield publisherResponse
    )
    .leftMap(mapBusinessErrorsToResults)
    .merge
    .recover(recovery(FAILED_TO_PUBLISH))
  }

  def validate: Action[JsValue] = Action.async(controllerComponents.parsers.json) { implicit request =>
    (
      for {
        _            <- ER.fromEither(ensureAuthorised.toRight(()).swap)
        apiAndScopes <- ER.fromEither(validateRequestPayload[ApiAndScopes])
        validation   <- ER.fromEitherF(publisherService.validation(apiAndScopes, validateApiDefinition = true).map(_.toRight(apiAndScopes).map(x => UnprocessableEntity(error(ErrorCode.INVALID_API_DEFINITION, Json.toJson(x)))).swap))
      }
      yield NoContent
    )
    .merge
    .recover(recovery(FAILED_TO_VALIDATE))
  }

  def fetchUnapprovedServices(): Action[AnyContent] = Action.async { _ =>
    approvalService.fetchUnapprovedServices().map {
      result => Ok(Json.toJson(result))
    } recover recovery(FAILED_TO_FETCH_UNAPPROVED_SERVICES)
  }

  def fetchServiceSummary(serviceName: String): Action[AnyContent] = Action.async { _ =>
    approvalService.fetchServiceApproval(serviceName)
      .map(res => Ok(Json.toJson(res)))
      .recover(recovery(FAILED_TO_FETCH_UNAPPROVED_SERVICES))
  }

  def approve(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    {
      for {
        serviceLocation <- approvalService.approveService(serviceName)
        result          <- publishService(serviceLocation).map {
                             case Result(ResponseHeader(OK, _, _), _, _, _, _) => NoContent
                             case other                                        => other
                           }
      } yield result
    } recover recovery(FAILED_TO_APPROVE_SERVICES)
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
