/*
 * Copyright 2021 HM Revenue & Customs
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

object ErrorCode extends Enumeration {

  type ErrorCode = Value
  val MALFORMED_JSON = Value("API_MALFORMED_JSON")
  val INVALID_REQUEST_PAYLOAD = Value("API_PUBLISHER_INVALID_REQUEST_PAYLOAD")
  val INVALID_API_DEFINITION = Value("API_PUBLISHER_INVALID_API_DEFINITION")
  val NO_SUCH_SCOPE = Value("API_NO_SUCH_SCOPE")
  val UNEXPECTED_ERROR = Value("API_UNEXPECTED_ERROR")
  val UNKNOWN_ERROR = Value("API_PUBLISHER_UNKNOWN_ERROR")
  val UNAUTHORIZED = Value("UNAUTHORIZED")
}


sealed trait ScopesDefinedResult

case object ScopesDefinedOk extends ScopesDefinedResult

//object ScopesNotDefined extends ScopesDefinedResult
case class ScopesNotDefined(message: String) extends ScopesDefinedResult