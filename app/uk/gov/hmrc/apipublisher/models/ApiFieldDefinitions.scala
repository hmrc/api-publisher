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

import cats.data.{NonEmptyList => NEL}
import cats.implicits._
import julienrf.json.derived
import julienrf.json.derived.TypeTagSetting
import play.api.libs.json._
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

case object UrlValidationRule extends ValidationRule

case class Validation(errorMessage: String, rules: NEL[ValidationRule])

object Validation {

  implicit val validationRuleFormat: OFormat[ValidationRule] = derived.withTypeTag.oformat(TypeTagSetting.ShortClassName)

  implicit val nelValidationRuleFormat: Format[NEL[ValidationRule]] = NonEmptyListOps.format[ValidationRule]

  implicit val ValidationJF = Json.format[Validation]
}

case class ApiFieldDefinitions(apiContext: String,
                               apiVersion: String,
                               fieldDefinitions: Seq[FieldDefinition])

object FieldDefinitionType extends Enumeration {
  type FieldDefinitionType = Value

  val URL = Value("URL")
  val SECURE_TOKEN = Value("SecureToken")
  val STRING = Value("STRING")
  val PPNS_FIELD = Value("PPNSField")

implicit val FieldDefitionTypeFormat: Format[FieldDefinitionType] =
    Format(
      Reads.enumNameReads(FieldDefinitionType),
      Writes.enumNameWrites[FieldDefinitionType.type]
    )
  
}

case class FieldDefinition(
  name: String, 
  description: String, 
  hint: Option[String],
  `type`: FieldDefinitionType.Value, 
  shortDescription: Option[String] = None, 
  validation: Option[Validation] = None, 
  access: AccessRequirements = AccessRequirements.Default)


object FieldDefinition {
  import AccessRequirementsFormatters._

  // implicit val FieldDefinitionReads: Format[FieldDefinition] = Json.format[FieldDefinition]

  implicit val FieldDefinitionReads: Reads[FieldDefinition] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "description").read[String] and
    (JsPath \ "hint").readNullable[String] and
    (JsPath \ "type").read[FieldDefinitionType] and
    (JsPath \ "shortDescription").readNullable[String] and
    (JsPath \ "validation").readNullable[Validation] and
    ((JsPath \ "access").read[AccessRequirements] or Reads.pure(AccessRequirements.Default))
  )(FieldDefinition.apply _)

  implicit val FieldDefinitionWrites: Writes[FieldDefinition] = new Writes[FieldDefinition] {

    def dropTail[A,B,C,D,E,F,G]( t: Tuple7[A,B,C,D,E,F,G] ): Tuple6[A,B,C,D,E,F] = (t._1, t._2, t._3, t._4, t._5, t._6)

    // This allows us to hide default AccessRequirements from JSON - as this is a rarely used field
    // but not one that business logic would want as an optional field and require getOrElse everywhere.
    override def writes(o: FieldDefinition): JsValue = {
      val common =
          (JsPath \ "name").write[String] and
          (JsPath \ "description").write[String] and
          (JsPath \ "hint").writeNullable[String] and
          (JsPath \ "type").write[FieldDefinitionType] and
          (JsPath \ "shortDescription").writeNullable[String] and
          (JsPath \ "validation").writeNullable[Validation]

      (if(o.access == AccessRequirements.Default) {
        (common)(unlift(FieldDefinition.unapply).andThen(dropTail))
      } else {
        (common and (JsPath \ "access").write[AccessRequirements])(unlift(FieldDefinition.unapply))
      }).writes(o)
    }
  }
}
