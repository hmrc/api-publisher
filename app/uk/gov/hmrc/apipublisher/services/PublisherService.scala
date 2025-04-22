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
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors.Process
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

  def publishAPIDefinition(serviceLocation: ServiceLocation, producerApiDefinition: ProducerApiDefinition)(implicit hc: HeaderCarrier): Future[PublicationResult] = {

    val apiDetailsWithServiceLocation: JsObject = {
      producerApiDefinition.apiWithoutFieldDefinitions ++ Json.obj(
        "serviceBaseUrl" -> serviceLocation.serviceUrl,
        "serviceName"    -> serviceLocation.serviceName
      )
    }

    def publish(producerApiDefinition: ProducerApiDefinition): Future[JsObject] = {
      for {
        _ <- apiDefinitionConnector.publishAPI(apiDetailsWithServiceLocation)
        _ <- publishFieldDefinitions(producerApiDefinition.fieldDefinitions)
        _ <- deleteRetiredSubscriptions()
      } yield apiDetailsWithServiceLocation
    }

    def deleteRetiredSubscriptions() = {
      Future.sequence(
        producerApiDefinition.retiredVersionNumbers.toList
          .map { version => tpaConnector.deleteSubscriptions(producerApiDefinition.apiContext, version) }
      )
    }

    def publishFieldDefinitions(fieldDefinitions: Seq[ApiFieldDefinitions]): Future[Unit] = {
      if (fieldDefinitions.nonEmpty) {
        apiSubscriptionFieldsConnector.publishFieldDefinitions(fieldDefinitions)
      } else {
        successful(())
      }
    }

    def createOrUpdateApprovalAndPublish(producerApiDefinition: ProducerApiDefinition): Future[PublicationResult] = {
      for {
        isApproved <- createOrUpdateApproval(serviceLocation, producerApiDefinition.apiName, producerApiDefinition.description)
        api        <- if (isApproved) publish(producerApiDefinition) else successful(apiDetailsWithServiceLocation)
      } yield PublicationResult(isApproved, api.as[PublisherResponse])
    }

    createOrUpdateApprovalAndPublish(producerApiDefinition)

  }

  def validation(producerApiDefinition: ProducerApiDefinition, validateApiDefinition: Boolean)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    def conditionalValidateApiDefinition(producerApiDefinition: ProducerApiDefinition, validateApiDefinition: Boolean)(implicit hc: HeaderCarrier) = {
      if (validateApiDefinition) {
        apiDefinitionConnector.validateAPIDefinition(producerApiDefinition.apiWithoutFieldDefinitions)
      } else {
        successful(None)
      }
    }

    def checkForErrors(): Future[Option[JsObject]] = {
      for {
        apiErrors       <- conditionalValidateApiDefinition(producerApiDefinition, validateApiDefinition)
        fieldDefnErrors <- apiSubscriptionFieldsConnector.validateFieldDefinitions(producerApiDefinition.fieldDefinitions.flatMap(_.fieldDefinitions))
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

  def createOrUpdateApproval(serviceLocation: ServiceLocation, apiName: String, apiDescription: Option[String]): Future[Boolean] = {
    val state       = ApiApprovalState(actor = Some(Process("Jenkins publish job")), statusChangedTo = Some(ApprovalStatus.NEW))
    val apiApproval = APIApproval(serviceLocation.serviceName, serviceLocation.serviceUrl, apiName, apiDescription, stateHistory = Seq(state))
    approvalService.createOrUpdateServiceApproval(apiApproval)
  }
}
