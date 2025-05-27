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

package uk.gov.hmrc.apipublisher.models

import java.time.Instant
import scala.collection.immutable.ListSet

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actor

import uk.gov.hmrc.apipublisher.models.ApprovalStatus.APPROVED

case class ApiApprovalState(
    actor: Actor,
    changedAt: Instant,
    status: Option[ApprovalStatus] = None,
    notes: Option[String] = None
  )

object ApiApprovalState {
  implicit val stateFormat: Format[ApiApprovalState] = Json.format[ApiApprovalState]
}

case class APIApproval(
    serviceName: String,
    serviceUrl: String,
    name: String,
    description: Option[String] = None,
    createdOn: Option[Instant] = Some(Instant.now()),
    approvedOn: Option[Instant] = None,
    lastUpdated: Option[Instant] = None,
    approvedBy: Option[Actor] = None,
    status: ApprovalStatus = ApprovalStatus.NEW,
    stateHistory: Seq[ApiApprovalState] = Seq.empty
  ) {
  def isApproved: Boolean = status == APPROVED
}

object APIApproval {
  implicit val apiApprovalFormat: Format[APIApproval] = Json.using[Json.WithDefaultValues].format[APIApproval]
}

sealed trait ApprovalStatus

object ApprovalStatus {
  case object NEW         extends ApprovalStatus
  case object FAILED      extends ApprovalStatus
  case object APPROVED    extends ApprovalStatus
  case object RESUBMITTED extends ApprovalStatus

  /* The order of the following declarations is important since it defines the ordering of the enumeration.
   * Be very careful when changing this, code may be relying on certain values being larger/smaller than others. */
  val values = ListSet(NEW, FAILED, APPROVED, RESUBMITTED)

  def apply(text: String): Option[ApprovalStatus] = ApprovalStatus.values.find(_.toString.toUpperCase == text.toUpperCase())

  def unsafeApply(text: String): ApprovalStatus = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid ApprovalStatus"))

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[ApprovalStatus] = SealedTraitJsonFormatting.createFormatFor[ApprovalStatus]("ApprovalStatus", apply)
}
