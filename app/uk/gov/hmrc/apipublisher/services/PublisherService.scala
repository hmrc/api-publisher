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

package uk.gov.hmrc.apipublisher.services

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import uk.gov.hmrc.apipublisher.connectors.{APIDefinitionConnector, APIScopeConnector, APISubscriptionFieldsConnector}
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, _}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import scala.util.{Success,Failure, Try}

@Singleton
class PublisherService @Inject()(apiDefinitionConnector: APIDefinitionConnector,
                                 apiSubscriptionFieldsConnector: APISubscriptionFieldsConnector,
                                 apiScopeConnector: APIScopeConnector,
                                 approvalService: ApprovalService)(implicit val ec: ExecutionContext) {

  def publishAPIDefinitionAndScopes(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes)(implicit hc: HeaderCarrier): Future[Boolean] = {

    def apiDetailsWithServiceLocation(apiAndScopes: ApiAndScopes): JsObject = {
      apiAndScopes.apiWithoutFieldDefinitions ++ Json.obj(
        "serviceBaseUrl" -> serviceLocation.serviceUrl,
        "serviceName" -> serviceLocation.serviceName)
    }

    def publish(apiAndScopes: ApiAndScopes): Future[Boolean] = {
      for {
        _ <- apiScopeConnector.publishScopes(apiAndScopes.scopes)
        _ <- apiDefinitionConnector.publishAPI(apiDetailsWithServiceLocation(apiAndScopes))
        _ <- publishFieldDefinitions(apiAndScopes.fieldDefinitions)
      } yield true
    }

    def publishFieldDefinitions(fieldDefinitions: Seq[ApiFieldDefinitions]): Future[Unit] = {
      if (fieldDefinitions.nonEmpty) {
        apiSubscriptionFieldsConnector.publishFieldDefinitions(fieldDefinitions)
      } else {
        successful(())
      }
    }

    def validateAndPublish(apiAndScopes: ApiAndScopes): Future[Boolean] = {
      for {
        isApproved <- checkApproval(serviceLocation, apiAndScopes.apiName, apiAndScopes.description)
        result <- if (isApproved) publish(apiAndScopes) else successful(false)
      } yield result
    }

    validateAndPublish(apiAndScopes)

  }

  def validateAPIDefinitionAndScopes(apiAndScopes: ApiAndScopes)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    validation(apiAndScopes)
  }


  def validation(apiAndScopes: ApiAndScopes, validateApiDefinition: Boolean = true)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    def conditionalValidateApiDefinition(apiAndScopes: ApiAndScopes, validateApiDefinition: Boolean)(implicit hc: HeaderCarrier) = {
      if(validateApiDefinition)
        apiDefinitionConnector.validateAPIDefinition(apiAndScopes.apiWithoutFieldDefinitions)
      else
        successful(None)
    }

    Try(apiAndScopes.validateAPIScopesAreDefined()) match {
      case Success(_) =>
        for {
          scopeErrors     <- apiScopeConnector.validateScopes(apiAndScopes.scopes)
          apiErrors       <- conditionalValidateApiDefinition(apiAndScopes, validateApiDefinition)
          fieldDefnErrors <- apiSubscriptionFieldsConnector.validateFieldDefinitions(apiAndScopes.fieldDefinitions.flatMap(_.fieldDefinitions))
        } yield {

          if (scopeErrors.isEmpty && apiErrors.isEmpty && fieldDefnErrors.isEmpty) {
            None
          } else {
            Some(
              JsObject(
                Seq.empty[(String, JsValue)] ++
                  scopeErrors.map("scopeErrors" -> _) ++
                  apiErrors.map("apiDefinitionErrors" -> _) ++
                  fieldDefnErrors.map("fieldDefinitionErrors" -> _)
              )
            )
          }
        }

      case Failure(e) =>
        val undefinedScopesErrorJson = Json.obj("scopeErrors" -> JsArray(Seq(Json.obj("field" -> "key", "message" -> e.getMessage))))
        successful(Some(undefinedScopesErrorJson))
    }
  }

  def checkApproval(serviceLocation: ServiceLocation, apiName: String, apiDescription: Option[String]): Future[Boolean] = {
    val apiApproval = APIApproval(serviceLocation.serviceName, serviceLocation.serviceUrl, apiName, apiDescription)
    approvalService.createOrUpdateServiceApproval(apiApproval)
  }

}
