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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors.Process
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow

import uk.gov.hmrc.apipublisher.config.AppConfig
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.ApprovalStatus.{APPROVED, FAILED, RESUBMITTED}
import uk.gov.hmrc.apipublisher.models.{APIApproval, ApiApprovalState, ApprovalStatus, ServiceLocation, ServicesSearch}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository
import uk.gov.hmrc.apipublisher.util.ApplicationLogger

@Singleton
class ApprovalService @Inject() (apiApprovalRepository: APIApprovalRepository, appContext: AppConfig, val clock: Clock)(implicit val ec: ExecutionContext)
    extends ApplicationLogger with ClockNow {

  def fetchAllServices(): Future[List[APIApproval]] = apiApprovalRepository.fetchAllServices().map(_.toList)

  def searchServices(searchCriteria: ServicesSearch): Future[List[APIApproval]] = apiApprovalRepository.searchServices(searchCriteria).map(_.toList)

  def createOrUpdateServiceApproval(apiApproval: APIApproval): Future[Boolean] = {

    def saveApproval(apiApproval: APIApproval, maybeExistingApiApproval: Option[APIApproval]): Future[APIApproval] =
      maybeExistingApiApproval match {
        case Some(existingApproval) => apiApprovalRepository.save(existingApproval.copy(
            status = if (existingApproval.status == FAILED) RESUBMITTED else existingApproval.status,
            stateHistory = if (existingApproval.status == FAILED) {
              existingApproval.stateHistory :+ ApiApprovalState(
                actor = Process("Publish process"),
                status = Some(ApprovalStatus.RESUBMITTED),
                notes = Some("Publish process"),
                changedAt = instant()
              )
            } else existingApproval.stateHistory
          ))
        case _                      => apiApprovalRepository.save(apiApproval)
      }

    for {
      maybeExistingApiApproval <- apiApprovalRepository.fetch(apiApproval.serviceName)
      savedApproval            <- saveApproval(apiApproval, maybeExistingApiApproval)
    } yield savedApproval.isApproved
  }

  def approveService(serviceName: String, actor: Actors.GatekeeperUser, notes: Option[String]): Future[ServiceLocation] =
    for {
      approval    <- fetchServiceApproval(serviceName)
      stateHistory = approval.stateHistory :+ ApiApprovalState(actor = actor, status = Some(APPROVED), notes = notes, changedAt = instant())
      _           <- apiApprovalRepository.save(approval.copy(status = APPROVED, approvedOn = Some(instant()), approvedBy = Some(actor), stateHistory = stateHistory))
    } yield {
      logger.info(s"Approved service $serviceName")
      ServiceLocation(approval.serviceName, approval.serviceUrl)
    }

  def declineService(serviceName: String, actor: Actors.GatekeeperUser, notes: Option[String]): Future[ServiceLocation] =
    for {
      approval    <- fetchServiceApproval(serviceName)
      stateHistory = approval.stateHistory :+ ApiApprovalState(actor = actor, status = Some(FAILED), notes = notes, changedAt = instant())
      _           <- apiApprovalRepository.save(approval.copy(status = FAILED, approvedOn = None, approvedBy = None, stateHistory = stateHistory))
    } yield {
      logger.info(s"Declined service $serviceName")
      ServiceLocation(approval.serviceName, approval.serviceUrl)
    }

  def addComment(serviceName: String, actor: Actors.GatekeeperUser, notes: Option[String]): Future[ServiceLocation] =
    for {
      approval    <- fetchServiceApproval(serviceName)
      stateHistory = approval.stateHistory :+ ApiApprovalState(actor = actor, status = None, notes = notes, changedAt = instant())
      _           <- apiApprovalRepository.save(approval.copy(stateHistory = stateHistory))
    } yield {
      logger.info(s"Added comment for service $serviceName")
      ServiceLocation(approval.serviceName, approval.serviceUrl)
    }

  def fetchServiceApproval(serviceName: String): Future[APIApproval] =
    apiApprovalRepository.fetch(serviceName).flatMap {
      case Some(a) => Future.successful(a)
      case None    => Future.failed(UnknownApiServiceException(s"Unable to Find Service. Unknown Service Name: $serviceName"))
    }

  def deleteApiApproval(serviceName: String): Future[Unit] =
    apiApprovalRepository.delete(serviceName)
}
