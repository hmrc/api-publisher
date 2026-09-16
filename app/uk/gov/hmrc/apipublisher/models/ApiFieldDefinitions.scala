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

import cats.data.{NonEmptyList => NEL}
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

import uk.gov.hmrc.apiplatform.modules.common.domain.services.SimpleEnumJsonFormatting

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

  given Format[RegexValidationRule] = Json.format[RegexValidationRule]
  given OFormat[UrlValidationRule.type] = Json.format[UrlValidationRule.type]
  
  given OFormat[ValidationRule] = new OFormat[ValidationRule] {
    override def reads(json: JsValue): JsResult[ValidationRule] = json match {
      case JsObject(fields) if(fields.contains("RegexValidationRule")) =>
        Json.fromJson[RegexValidationRule](fields("RegexValidationRule"))
      case JsObject(fields) if(fields.contains("UrlValidationRule")) =>
        Json.fromJson[UrlValidationRule.type](fields("UrlValidationRule"))
      case x: JsValue => {
        JsError(s"Not a validation rule $x")
      }
    }

    override def writes(o: ValidationRule): JsObject = o match {
      case r: RegexValidationRule => JsObject(Seq("RegexValidationRule" -> Json.toJson(r)))
      case UrlValidationRule => JsObject(Seq(("UrlValidationRule" -> JsObject(Seq.empty))))
    }
  }
  
  given Format[NEL[ValidationRule]] = NonEmptyListOps.format[ValidationRule]
  given Format[Validation]          = Json.format[Validation]
}

case class ApiFieldDefinitions(apiContext: String, apiVersion: String, fieldDefinitions: Seq[FieldDefinition])

enum FieldDefinitionType {
  @deprecated("We don't use URL type for any validation", since = "0.5x") case Url
  case SecureToken, PlainText, PPNSField
}

object FieldDefinitionType {

  extension (fdt: FieldDefinitionType) {
    def label = FieldDefinitionType.labelMe(fdt)
  }

  def apply(text: String): Option[FieldDefinitionType] = FieldDefinitionType.values.find(_.label == text)

  def unsafeApply(text: String): FieldDefinitionType = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid Field Definition Type"))

  private def labelMe(fdt: FieldDefinitionType): String = fdt match {
    case Url         => "URL"
    case SecureToken => "SecureToken"
    case PlainText   => "STRING"
    case PPNSField   => "PPNSField"
  }

  given Format[FieldDefinitionType] = SimpleEnumJsonFormatting.createFormatFor[FieldDefinitionType]("Field Definition Type", apply, label)
}

case class FieldDefinition(
    name: String,
    description: String,
    hint: Option[String],
    `type`: FieldDefinitionType,
    shortDescription: Option[String] = None,
    validation: Option[Validation] = None,
    access: AccessRequirements = AccessRequirements.Default
  )

object FieldDefinition {
  import AccessRequirementsFormatters.given

  // implicit val FieldDefinitionReads: Format[FieldDefinition] = Json.format[FieldDefinition]

  given Reads[FieldDefinition] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "hint").readNullable[String] and
      (JsPath \ "type").read[FieldDefinitionType] and
      (JsPath \ "shortDescription").readNullable[String] and
      (JsPath \ "validation").readNullable[Validation] and
      ((JsPath \ "access").read[AccessRequirements] or Reads.pure(AccessRequirements.Default))
  )(FieldDefinition.apply)

  given Writes[FieldDefinition] = new Writes[FieldDefinition] {

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

      (
        if (o.access == AccessRequirements.Default) {
          // (common)(unlift(FieldDefinition.unapply).andThen(dropTail))
          (common)( (fd: FieldDefinition) => (fd.name, fd.description, fd.hint, fd.`type`, fd.shortDescription, fd.validation))
        } else {
          (common and (JsPath \ "access").write[AccessRequirements])(
            (fd: FieldDefinition) => (fd.name, fd.description, fd.hint, fd.`type`, fd.shortDescription, fd.validation, fd.access)
          )
        }
      ).writes(o)
    }
  }
}
