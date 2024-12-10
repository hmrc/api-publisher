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

import uk.gov.hmrc.apipublisher.connectors.{APIDefinitionConnector, APISubscriptionFieldsConnector, TpaConnector}
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

@Singleton
class PublisherService @Inject() (
    apiDefinitionConnector: APIDefinitionConnector,
    apiSubscriptionFieldsConnector: APISubscriptionFieldsConnector,
    tpaConnector: TpaConnector,
    approvalService: ApprovalService
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  def publishAPIDefinition(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes)(implicit hc: HeaderCarrier): Future[PublicationResult] = {

    val apiDetailsWithServiceLocation: JsObject = {
      apiAndScopes.apiWithoutFieldDefinitions ++ Json.obj(
        "serviceBaseUrl" -> serviceLocation.serviceUrl,
        "serviceName"    -> serviceLocation.serviceName
      )
    }

    def publish(apiAndScopes: ApiAndScopes): Future[JsObject] = {
      for {
        _ <- apiDefinitionConnector.publishAPI(apiDetailsWithServiceLocation)
        _ <- publishFieldDefinitions(apiAndScopes.fieldDefinitions)
        _ <- deleteRetiredSubscriptions()
      } yield apiDetailsWithServiceLocation
    }

    def deleteRetiredSubscriptions() = {
      Future.sequence(
        apiAndScopes.retiredVersionNumbers.toList
          .map { version => tpaConnector.deleteSubscriptions(apiAndScopes.apiContext, version) }
      )
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
        api        <- if (isApproved) publish(apiAndScopes) else successful(apiDetailsWithServiceLocation)
      } yield PublicationResult(isApproved, api.as[PublisherResponse])
    }

    checkApprovedAndPublish(apiAndScopes)

  }

  def validation(apiAndScopes: ApiAndScopes, validateApiDefinition: Boolean)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    def conditionalValidateApiDefinition(apiAndScopes: ApiAndScopes, validateApiDefinition: Boolean)(implicit hc: HeaderCarrier) = {
      if (validateApiDefinition) {
        apiDefinitionConnector.validateAPIDefinition(apiAndScopes.apiWithoutFieldDefinitions)
      } else {
        successful(None)
      }
    }

    def checkForErrors(): Future[Option[JsObject]] = {
      for {
        apiErrors       <- conditionalValidateApiDefinition(apiAndScopes, validateApiDefinition)
        fieldDefnErrors <- apiSubscriptionFieldsConnector.validateFieldDefinitions(apiAndScopes.fieldDefinitions.flatMap(_.fieldDefinitions))
      } yield {
        if (apiErrors.isEmpty && fieldDefnErrors.isEmpty) {
          None
        } else {
          Some(
            JsObject(
              Seq.empty[(String, JsValue)] ++
                apiErrors.map("apiDefinitionErrors" -> _) ++
                fieldDefnErrors.map("fieldDefinitionErrors" -> _)
            )
          )
        }
      }
    }

    checkForErrors()
  }

  def checkApproval(serviceLocation: ServiceLocation, apiName: String, apiDescription: Option[String]): Future[Boolean] = {
    val apiApproval = APIApproval(serviceLocation.serviceName, serviceLocation.serviceUrl, apiName, apiDescription)
    approvalService.createOrUpdateServiceApproval(apiApproval)
  }
}
