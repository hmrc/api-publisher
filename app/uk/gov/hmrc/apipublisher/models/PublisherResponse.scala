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

import play.api.libs.json.*
import uk.gov.hmrc.apiplatform.modules.common.domain.services.SimpleEnumJsonFormatting

case class PublicationResult(approved: Boolean, publisherResponse: PublisherResponse)

case class PublisherResponse(name: String, serviceName: String, context: String, description: String, versions: List[PublisherApiVersion])

object PublisherResponse {
  given OFormat[PublisherResponse] = Json.format[PublisherResponse]
}

case class PublisherApiVersion(version: String, status: PublisherApiStatus)

object PublisherApiVersion {
  given OFormat[PublisherApiVersion] = Json.format[PublisherApiVersion]
}

enum PublisherApiStatus {
  case Alpha, Beta, Stable, Deprecated, Retired
}

object PublisherApiStatus {
  // case object ALPHA      extends PublisherApiStatus
  // case object BETA       extends PublisherApiStatus
  // case object STABLE     extends PublisherApiStatus
  // case object DEPRECATED extends PublisherApiStatus
  // case object RETIRED    extends PublisherApiStatus

  // When the api-definition service stops returning PROTOTYPED and PUBLISHED, the conversions below can be removed
  def apply(text: String): Option[PublisherApiStatus] = text.toUpperCase() match {
    case "ALPHA"                => Some(Alpha)
    case "PROTOTYPED" | "BETA"  => Some(Beta)
    case "PUBLISHED" | "STABLE" => Some(Stable)
    case "DEPRECATED"           => Some(Deprecated)
    case "RETIRED"              => Some(Retired)
    case _                      => None
  }

  def unsafeApply(text: String): PublisherApiStatus = {
    apply(text).getOrElse(throw new RuntimeException(s"$text is not a status"))
  }

  given Format[PublisherApiStatus] = SimpleEnumJsonFormatting.createStringFormatFor[PublisherApiStatus]("PublisherApiStatus", apply, _.toString.toUpperCase)
}
