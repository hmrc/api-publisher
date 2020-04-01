/*
 * Copyright 2020 HM Revenue & Customs
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

import cats.data.{NonEmptyList => NEL}
import cats.implicits._
import julienrf.json.derived
import julienrf.json.derived.TypeTagSetting
import play.api.libs.json.{Format, Json, JsonValidationError, OFormat, Reads, Writes}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.apipublisher.models.FieldDefinitionType.FieldDefinitionType


object NonEmptyListOps {
  def reads[T: Reads]: Reads[NEL[T]] =
    Reads
      .of[List[T]]
      .collect(
        JsonValidationError("expected a NonEmptyList but got an empty list")
      ) {
        case head :: tail => NEL(head, tail)
      }

  def writes[T: Writes]: Writes[NEL[T]] =
    Writes
      .of[List[T]]
      .contramap(_.toList)

  def format[T: Format]: Format[NEL[T]] =
    Format(reads, writes)
}

sealed trait ValidationRule

case class RegexValidationRule(regex: String) extends ValidationRule

case class Validation(errorMessage: String, rules: NEL[ValidationRule])

object Validation {

  implicit val validationRuleFormat: OFormat[ValidationRule] = derived.withTypeTag.oformat(TypeTagSetting.ShortClassName)

  implicit val nelValidationRuleFormat: Format[NEL[ValidationRule]] = NonEmptyListOps.format[ValidationRule]

  implicit val ValidationJF = Json.format[Validation]
}

case class ApiFieldDefinitions(apiContext: String,
                               apiVersion: String,
                               fieldDefinitions: Seq[FieldDefinition])

case class FieldDefinition(name: String, description: String, hint: Option[String],
                           `type`: FieldDefinitionType, shortDescription: Option[String] = None, validation: Option[Validation] = None)

object FieldDefinition {
  implicit val format = {
    implicit val format: Format[FieldDefinitionType] = Format(
      Reads.enumNameReads(FieldDefinitionType),
      Writes.enumNameWrites[FieldDefinitionType.type])
    Json.format[FieldDefinition]
  }
}

object FieldDefinitionType extends Enumeration {
  type FieldDefinitionType = Value

  val URL = Value("URL")
  val SECURE_TOKEN = Value("SecureToken")
  val STRING = Value("STRING")
}