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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actor

case class APIApproval(
    serviceName: String,
    serviceUrl: String,
    name: String,
    description: Option[String] = None,
    approved: Option[Boolean] = Some(false),
    createdOn: Option[Instant] = Some(Instant.now()),
    approvedOn: Option[Instant] = None,
    approvedBy: Option[Actor] = None
  ) {
  def isApproved: Boolean = approved.getOrElse(false)
}

object APIApproval {
  implicit val apiApprovalFormat: Format[APIApproval] = Json.format[APIApproval]
}
