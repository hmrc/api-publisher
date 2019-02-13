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

package uk.gov.hmrc.apipublisher.services

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.apipublisher.wiring.AppContext
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{APIApproval, ServiceLocation}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ApprovalService @Inject()(apiApprovalRepository: APIApprovalRepository, appContext: AppContext) {

  def fetchUnapprovedServices(): Future[Seq[APIApproval]] = apiApprovalRepository.fetchUnapprovedServices()

  def createOrUpdateServiceApproval(apiApproval: APIApproval): Future[Boolean] = {

    def calculateApiApprovalStatus(existingApiApproval: Option[APIApproval]): Boolean =
      (appContext.preventAutoDeploy, existingApiApproval) match {
        case (false, _) => true
        case (_, Some(e)) => e.isApproved
        case (_, _) => false
      }

    for {
      existingApiApproval <- apiApprovalRepository.fetch(apiApproval.serviceName)
      isApproved = calculateApiApprovalStatus(existingApiApproval)
      _ <- apiApprovalRepository.save(apiApproval.copy(approved = Some(isApproved)))
    } yield isApproved
  }

  def approveService(serviceName: String): Future[ServiceLocation] =
    for {
      approval <- fetchServiceApproval(serviceName)
      _ <- apiApprovalRepository.save(approval.copy(approved = Some(true)))
    } yield {
      Logger.info(s"Approved service $serviceName")
      ServiceLocation(approval.serviceName, approval.serviceUrl)
    }

  def fetchServiceApproval(serviceName: String): Future[APIApproval] =
    apiApprovalRepository.fetch(serviceName).flatMap {
      case Some(a) => Future.successful(a)
      case None => Future.failed(UnknownApiServiceException(s"Unable to Find Service. Unknown Service Name: $serviceName"))
    }
}
