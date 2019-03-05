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

package uk.gov.hmrc.apipublisher.models

import play.api.Configuration
import uk.gov.hmrc.apipublisher.models.ApiStatus.APIStatus

case class ApiDefinition(serviceName: String,
                         name: String,
                         context: String,
                         versions: Seq[ApiVersion],
                         requiresTrust: Option[Boolean],
                         isTestSupport: Option[Boolean] = None)

case class ApiVersion(version: String,
                      status: APIStatus,
                      access: Option[ApiAccess])

case class ApiAccess(`type`: APIAccessType.Value, whitelistedApplicationIds: Option[Seq[String]])

object ApiAccess {
  def build(config: Option[Configuration]): ApiAccess = ApiAccess(
    `type` = APIAccessType.PRIVATE,
    whitelistedApplicationIds = config.flatMap(_.getStringSeq("whitelistedApplicationIds")).orElse(Some(Seq.empty)))
}

object ApiStatus extends Enumeration {
  type APIStatus = Value
  val ALPHA, BETA, STABLE, DEPRECATED, RETIRED = Value
}

object APIAccessType extends Enumeration {
  type APIAccessType = Value
  val PRIVATE, PUBLIC = Value
}

case class VersionSubscription(version: ApiVersion, subscribed: Boolean)
