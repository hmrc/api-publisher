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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow

import uk.gov.hmrc.apipublisher.config.AppConfig
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.ApprovalStatus.{APPROVED, FAILED, NEW}
import uk.gov.hmrc.apipublisher.models.{APIApproval, ServiceLocation, ServicesSearch}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

@Singleton
class ApprovalService @Inject() (apiApprovalRepository: APIApprovalRepository, appContext: AppConfig, val clock: Clock)(implicit val ec: ExecutionContext)
    extends ApplicationLogger with ClockNow {

  def fetchUnapprovedServices(): Future[List[APIApproval]] = apiApprovalRepository.fetchUnapprovedServices().map(_.toList)

  def fetchAllServices(): Future[List[APIApproval]] = apiApprovalRepository.fetchAllServices().map(_.toList)

  def searchServices(searchCriteria: ServicesSearch): Future[List[APIApproval]] = apiApprovalRepository.searchServices(searchCriteria).map(_.toList)

  def createOrUpdateServiceApproval(apiApproval: APIApproval): Future[Boolean] = {

    def calculateApiApprovalStatus(existingApiApproval: Option[APIApproval]): Boolean =
      (appContext.preventAutoDeploy, existingApiApproval) match {
        case (false, _)   => true
        case (_, Some(e)) => e.isApproved
        case (_, _)       => false
      }

    def saveApproval(apiApproval: APIApproval, maybeExistingApiApproval: Option[APIApproval], isApproved: Boolean): Future[APIApproval] =
      maybeExistingApiApproval match {
        case Some(existingApproval) => apiApprovalRepository.save(apiApproval.copy(
            approved = Some(isApproved),
            createdOn = existingApproval.createdOn,
            approvedOn = existingApproval.approvedOn,
            approvedBy = existingApproval.approvedBy,
            status = existingApproval.status
          ))
        case _                      => apiApprovalRepository.save(apiApproval.copy(approved = Some(isApproved)))
      }

    for {
      maybeExistingApiApproval <- apiApprovalRepository.fetch(apiApproval.serviceName)
      isApproved                = calculateApiApprovalStatus(maybeExistingApiApproval)
      _                        <- saveApproval(apiApproval, maybeExistingApiApproval, isApproved)
    } yield isApproved
  }

  def approveService(serviceName: String, actor: Actors.GatekeeperUser): Future[ServiceLocation] =
    for {
      approval <- fetchServiceApproval(serviceName)
      _        <- apiApprovalRepository.save(approval.copy(approved = Some(true), status = APPROVED, approvedOn = Some(instant()), approvedBy = Some(actor)))
    } yield {
      logger.info(s"Approved service $serviceName")
      ServiceLocation(approval.serviceName, approval.serviceUrl)
    }

  def declineService(serviceName: String): Future[ServiceLocation] =
    for {
      approval <- fetchServiceApproval(serviceName)
      _        <- apiApprovalRepository.save(approval.copy(approved = Some(false), status = FAILED))
    } yield {
      logger.info(s"Declined service $serviceName")
      ServiceLocation(approval.serviceName, approval.serviceUrl)
    }

  def fetchServiceApproval(serviceName: String): Future[APIApproval] =
    apiApprovalRepository.fetch(serviceName).flatMap {
      case Some(a) => Future.successful(a)
      case None    => Future.failed(UnknownApiServiceException(s"Unable to Find Service. Unknown Service Name: $serviceName"))
    }

  def migrateApprovedFlag(): Future[Seq[APIApproval]] = {
    def migrateApprovedFlagToStatus(approval: APIApproval) = {
      approval.copy(status = if (approval.isApproved) APPROVED else NEW)
    }

    for {
      approvals       <- apiApprovalRepository.fetchAllServices()
      updatedApprovals = approvals.map(migrateApprovedFlagToStatus)
      res             <- Future.sequence(updatedApprovals.map(apiApprovalRepository.save))
    } yield res
  }
}
