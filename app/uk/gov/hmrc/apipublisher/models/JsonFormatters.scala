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

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

object JsonFormatters {

  implicit val formatAPIStatus = APIStatusJson.apiStatusFormat(ApiStatus)
  implicit val formatAPIAccessType = EnumJson.enumFormat(APIAccessType)
  implicit val formatAPIAccess = Json.format[ApiAccess]
  implicit val formatAPIVersion = Json.format[ApiVersion]

  val apiDefinitionReads: Reads[ApiDefinition] = (
    (JsPath \ "serviceName").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "context").read[String] and
      (JsPath \ "versions").read[Seq[ApiVersion]] and
      (JsPath \ "requiresTrust").readNullable[Boolean] and
      (JsPath \ "isTestSupport").readNullable[Boolean]
    ) (ApiDefinition.apply _)

  implicit val formatAPIDefinition = {
    Format(apiDefinitionReads, Json.writes[ApiDefinition])
  }

}

object EnumJson {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) =>
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException =>
            throw new InvalidEnumException(enum.getClass.getSimpleName, s)
        }
      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }

}

class InvalidEnumException(className: String, input: String)
  extends RuntimeException(s"Enumeration expected of type: '$className', but it does not contain '$input'")

object APIStatusJson {

  def apiStatusReads[APIStatus](apiStatus: APIStatus): Reads[ApiStatus.Value] = new Reads[ApiStatus.Value] {
    def reads(json: JsValue): JsResult[ApiStatus.Value] = json match {
      case JsString("PROTOTYPED") => JsSuccess(ApiStatus.BETA)
      case JsString("PUBLISHED") => JsSuccess(ApiStatus.STABLE)
      case JsString(s) =>
        try {
          JsSuccess(ApiStatus.withName(s))
        } catch {
          case _: NoSuchElementException =>
            JsError(s"Enumeration expected of type: ApiStatus, but it does not contain '$s'")
        }
      case _ => JsError("String value expected")
    }
  }

  implicit def apiStatusWrites: Writes[ApiStatus.Value] = new Writes[ApiStatus.Value] {
    def writes(v: ApiStatus.Value): JsValue = JsString(v.toString)
  }

  implicit def apiStatusFormat[APIStatus](apiStatus: APIStatus): Format[ApiStatus.Value] = {
    Format(apiStatusReads(apiStatus), apiStatusWrites)
  }

}