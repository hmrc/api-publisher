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

package uk.gov.hmrc.apipublisher.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apipublisher.connectors.{APIDefinitionConnector, APIScopeConnector, APISubscriptionFieldsConnector}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, _}
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

@Singleton
class PublisherService @Inject() (
    apiDefinitionConnector: APIDefinitionConnector,
    apiSubscriptionFieldsConnector: APISubscriptionFieldsConnector,
    apiScopeConnector: APIScopeConnector,
    approvalService: ApprovalService
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  def publishAPIDefinitionAndScopes(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes)(implicit hc: HeaderCarrier): Future[PublicationResult] = {

    def apiDetailsWithServiceLocation(apiAndScopes: ApiAndScopes): JsObject = {
      apiAndScopes.apiWithoutFieldDefinitions ++ Json.obj(
        "serviceBaseUrl" -> serviceLocation.serviceUrl,
        "serviceName"    -> serviceLocation.serviceName
      )
    }

    def publish(apiAndScopes: ApiAndScopes): Future[JsObject] = {
      for {
        _  <- apiScopeConnector.publishScopes(apiAndScopes.scopes)
        api = apiDetailsWithServiceLocation(apiAndScopes)
        _  <- apiDefinitionConnector.publishAPI(api)
        _  <- publishFieldDefinitions(apiAndScopes.fieldDefinitions)
      } yield api
    }

    def publishFieldDefinitions(fieldDefinitions: Seq[ApiFieldDefinitions]): Future[Unit] = {
      if (fieldDefinitions.nonEmpty) {
        apiSubscriptionFieldsConnector.publishFieldDefinitions(fieldDefinitions)
      } else {
        successful(())
      }
    }

    def checkApprovedAndPublish(apiAndScopes: ApiAndScopes): Future[PublicationResult] = {
      for {
        isApproved <- checkApproval(serviceLocation, apiAndScopes.apiName, apiAndScopes.description)
        api        <- if (isApproved) publish(apiAndScopes) else successful(apiDetailsWithServiceLocation(apiAndScopes))
      } yield PublicationResult(isApproved, api.as[PublisherResponse])
    }

    checkApprovedAndPublish(apiAndScopes)

  }

  def validateAPIDefinitionAndScopes(apiAndScopes: ApiAndScopes)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    validation(apiAndScopes)
  }

  def validation(apiAndScopes: ApiAndScopes, validateApiDefinition: Boolean = true)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    def conditionalValidateApiDefinition(apiAndScopes: ApiAndScopes, validateApiDefinition: Boolean)(implicit hc: HeaderCarrier) = {
      if (validateApiDefinition) {
        apiDefinitionConnector.validateAPIDefinition(apiAndScopes.apiWithoutFieldDefinitions)
      } else {
        successful(None)
      }
    }

    def checkScopesForErrors(scopeServiceScopes: Seq[Scope], scopeSeq: Seq[Scope]): Future[Option[JsObject]] = {
      for {
        scopeErrors        <- apiScopeConnector.validateScopes(apiAndScopes.scopes)
        scopeChangedErrors <- successful(scopesRemainUnchanged(scopeServiceScopes, scopeSeq))
        apiErrors          <- conditionalValidateApiDefinition(apiAndScopes, validateApiDefinition)
        fieldDefnErrors    <- apiSubscriptionFieldsConnector.validateFieldDefinitions(apiAndScopes.fieldDefinitions.flatMap(_.fieldDefinitions))
      } yield {
        if (scopeErrors.isEmpty && scopeChangedErrors.isEmpty && apiErrors.isEmpty && fieldDefnErrors.isEmpty) {
          None
        } else {
          Some(
            JsObject(
              Seq.empty[(String, JsValue)] ++
                scopeErrors.map("scopeErrors" -> _) ++
                scopeChangedErrors.map("scopeChangedErrors" -> _) ++
                apiErrors.map("apiDefinitionErrors" -> _) ++
                fieldDefnErrors.map("fieldDefinitionErrors" -> _)
            )
          )
        }
      }
    }

    def scopesRemainUnchanged(serviceScopes: Seq[Scope], scopeSeq: Seq[Scope]): Option[JsValue] = {
      if (scopeSeq.forall(serviceScopes.contains)) {
        None
      } else {
        logger.error(s"API name: ${apiAndScopes.apiName}, declared scopes: $scopeSeq,\nretrieved scopes: $serviceScopes")
        Some(JsString("Updating scopes while publishing is no longer supported. " +
          "See https://confluence.tools.tax.service.gov.uk/display/TEC/2021/09/07/Changes+to+scopes for more information"))
      }
    }

    val scopeSeq: Seq[Scope]      = apiAndScopes.scopes.as[Seq[Scope]]
    val scopesSearch: Set[String] = (scopeSeq.map(s => s.key).toList ++ apiAndScopes.apiScopes).toSet

    apiScopeConnector.retrieveScopes(scopesSearch) flatMap { scopeServiceScopes =>
      ApiAndScopes.validateAPIScopesAreDefined(apiAndScopes, scopeServiceScopes) match {
        case ScopesDefinedOk       => checkScopesForErrors(scopeServiceScopes, scopeSeq)
        case ScopesNotDefined(msg) =>
          val undefinedScopesErrorJson = Json.obj("scopeErrors" -> JsArray(Seq(Json.obj("field" -> "key", "message" -> msg))))
          successful(Some(undefinedScopesErrorJson))
      }
    }
  }

  def checkApproval(serviceLocation: ServiceLocation, apiName: String, apiDescription: Option[String]): Future[Boolean] = {
    val apiApproval = APIApproval(serviceLocation.serviceName, serviceLocation.serviceUrl, apiName, apiDescription)
    approvalService.createOrUpdateServiceApproval(apiApproval)
  }
}
