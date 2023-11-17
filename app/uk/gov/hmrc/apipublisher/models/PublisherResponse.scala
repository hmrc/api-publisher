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

import play.api.libs.json._

case class PublicationResult(approved: Boolean, publisherResponse: PublisherResponse)

case class PublisherResponse(name: String, serviceName: String, context: String, description: String, versions: List[PublisherApiVersion])

object PublisherResponse {
  implicit val format: OFormat[PublisherResponse] = Json.format[PublisherResponse]
}

case class PublisherApiVersion(version: String, status: PublisherApiStatus)

object PublisherApiVersion {
  implicit val format: OFormat[PublisherApiVersion] = Json.format[PublisherApiVersion]
}

sealed trait PublisherApiStatus

object PublisherApiStatus {
  case object ALPHA      extends PublisherApiStatus
  case object BETA       extends PublisherApiStatus
  case object STABLE     extends PublisherApiStatus
  case object DEPRECATED extends PublisherApiStatus
  case object RETIRED    extends PublisherApiStatus

  // When the api-definition service stops returning PROTOTYPED and PUBLISHED, the conversions below can be removed
  def apply(text: String): Option[PublisherApiStatus] = text.toUpperCase() match {
    case "ALPHA"                => Some(ALPHA)
    case "PROTOTYPED" | "BETA"  => Some(BETA)
    case "PUBLISHED" | "STABLE" => Some(STABLE)
    case "DEPRECATED"           => Some(DEPRECATED)
    case "RETIRED"              => Some(RETIRED)
    case _                      => None
  }

  private val convert: String => JsResult[PublisherApiStatus] = s => PublisherApiStatus(s).fold[JsResult[PublisherApiStatus]](JsError(s"$s is not a status"))(status => JsSuccess(status))

  implicit val reads: Reads[PublisherApiStatus] = JsPath.read[String].flatMapResult(convert(_))

  implicit val writes: Writes[PublisherApiStatus] = Writes[PublisherApiStatus](status => JsString(status.toString))

  implicit val format: Format[PublisherApiStatus] = Format(reads, writes)
}
