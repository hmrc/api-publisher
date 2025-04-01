/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apipublisher.scheduled

import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import uk.gov.hmrc.apipublisher.services.ApprovalService
import uk.gov.hmrc.apipublisher.util.ApplicationLogger
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

// $COVERAGE-OFF$

@Singleton
class MigrateApprovedFlagJob @Inject() (
    mongoLockRepository: MongoLockRepository,
    timestampSupport: TimestampSupport,
    approvalService: ApprovalService,
    configuration: Configuration
  )(implicit
    actorSystem: ActorSystem,
    ec: ExecutionContext
  ) extends ApplicationLogger {



  val initialDelay = configuration.get[FiniteDuration]("migrateApprovedFlag.initialDelay")
  val interval     = configuration.get[FiniteDuration]("migrateApprovedFlag.interval")
  val jobEnabled   = configuration.get[Boolean]("migrateApprovedFlag.enabled")

  val lockService =
    ScheduledLockService(
      lockRepository = mongoLockRepository,
      lockId = "migrate-approved-flag-lock",
      timestampSupport = timestampSupport,
      schedulerInterval = interval
    )

  if (jobEnabled) {
    logger.info("MigrateApprovedFlagJob enabled")
    actorSystem.scheduler.scheduleWithFixedDelay(initialDelay, interval) { () =>
      lockService.withLock {
        logger.info(s"Acquired lock. Starting MigrateApprovedFlagJob run")
        approvalService.migrateApprovedFlag()
      }.map {
        case Some(res) => logger.info(s"Finished with ${res.size} approvals migrated. Lock has been disowned and will expire 1 sec before next scheduled run.")
        case None      => logger.info("Failed to take lock")
      }.recoverWith {
        case e =>
          logger.error("Failed to execute job", e)
          Future.successful(())
      }
    }
  } else {
    logger.info("MigrateApprovedFlagJob disabled")
  }
}
// $COVERAGE-ON$