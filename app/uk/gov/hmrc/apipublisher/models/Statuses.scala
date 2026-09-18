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

enum ErrorCode {
  case InvalidRequestPayload, InvalidApiDefinition, UnknownError, Unauthorized, BadQueryParameter

  def asText = this match {
    case InvalidRequestPayload => "API_PUBLISHER_INVALID_REQUEST_PAYLOAD"
    case InvalidApiDefinition  => "API_PUBLISHER_INVALID_API_DEFINITION"
    case UnknownError          => "API_PUBLISHER_UNKNOWN_ERROR"
    case Unauthorized          => "UNAUTHORIZED"
    case BadQueryParameter     => "BAD_QUERY_PARAMETER"
  }
}

sealed trait ScopesDefinedResult

case object ScopesDefinedOk extends ScopesDefinedResult

case class ScopesNotDefined(message: String) extends ScopesDefinedResult
