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

// /*
//  * Copyright 2020 HM Revenue & Customs
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package uk.gov.hmrc.apipublisher.model


// import julienrf.json.derived
// import play.api.libs.json._
// import play.api.libs.functional.syntax._
// import uk.gov.hmrc.apipublisher.models.FieldDefinitionType.FieldDefinitionType
// import play.api.libs.json.Json.JsValueWrapper
// import uk.gov.hmrc.apipublisher.models.DevhubAccessLevel.{Admininstator,Developer}

// trait AccessRequirementsFormatters {

//   def ignoreDefaultField[T](value: T, default: T, jsonFieldName: String)(implicit w: Writes[T]) =
//     if(value == default) None else Some((jsonFieldName, Json.toJsFieldJsValueWrapper(value)))

//   implicit val DevhubAccessRequirementFormat: Format[DevhubAccessRequirement] = new Format[DevhubAccessRequirement] {

//     override def writes(o: DevhubAccessRequirement): JsValue = JsString(o match {
//       case Admininstator => "administrator"
//       case Developer => "developer"
//       case DevhubAccessRequirement.NoOne => "noone"
//     })

//     override def reads(json: JsValue): JsResult[DevhubAccessRequirement] = json match {
//       case JsString("administrator") => JsSuccess(Admininstator)
//       case JsString("developerdeveloper") => JsSuccess(Developer)
//       case JsString("noone") => JsSuccess(DevhubAccessRequirement.NoOne)
//       case _ => JsError("Not a recognized DevhubAccessRequirement")
//     }
//   }

//   implicit val DevhubAccessRequirementsReads: Reads[DevhubAccessRequirements] = (
//     ((JsPath \ "readOnly").read[DevhubAccessRequirement] or Reads.pure(DevhubAccessRequirement.Default)) and
//     ((JsPath \ "readWrite").read[DevhubAccessRequirement] or Reads.pure(DevhubAccessRequirement.Default))
//   )(DevhubAccessRequirements.apply _)

//   implicit val DevhubAccessRequirementsWrites: OWrites[DevhubAccessRequirements] = new OWrites[DevhubAccessRequirements] {
//     def writes(requirements: DevhubAccessRequirements) = {
//       Json.obj(
//         (
//           ignoreDefaultField(requirements.readOnly, DevhubAccessRequirement.Default, "readOnly") ::
//           ignoreDefaultField(requirements.readWrite, DevhubAccessRequirement.Default, "readWrite") ::
//           List.empty[Option[(String, JsValueWrapper)]]
//         ).filterNot(_.isEmpty).map(_.get): _*
//       )
//     }
//   }

//   implicit val AccessRequirementsReads: Reads[AccessRequirements] = Json.reads[AccessRequirements]

//   implicit val AccessRequirementsWrites: Writes[AccessRequirements] = Json.writes[AccessRequirements]
// }

// trait JsonFormatters extends NonEmptyListFormatters with AccessRequirementsFormatters {

//   implicit val FieldDefinitionTypeReads = Reads.enumNameReads(FieldDefinitionType)

//   implicit val FieldDefinitionReads: Reads[FieldDefinition] = (
//     (JsPath \ "name").read[FieldName] and
//     (JsPath \ "description").read[String] and
//     ((JsPath \ "hint").read[String] or Reads.pure("")) and
//     (JsPath \ "type").read[FieldDefinitionType] and
//     ((JsPath \ "shortDescription").read[String] or Reads.pure("")) and
//     (JsPath \ "validation").readNullable[ValidationGroup] and
//     ((JsPath \ "access").read[AccessRequirements] or Reads.pure(AccessRequirements.Default))
//   )(FieldDefinition.apply _)

//   implicit val FieldDefinitionWrites: Writes[FieldDefinition] = new Writes[FieldDefinition] {

//     def dropTail[A,B,C,D,E,F,G]( t: Tuple7[A,B,C,D,E,F,G] ): Tuple6[A,B,C,D,E,F] = (t._1, t._2, t._3, t._4, t._5, t._6)

//     // This allows us to hide default AccessRequirements from JSON - as this is a rarely used field
//     // but not one that business logic would want as an optional field and require getOrElse everywhere.
//     override def writes(o: FieldDefinition): JsValue = {
//       val common =
//           (JsPath \ "name").write[FieldName] and
//           (JsPath \ "description").write[String] and
//           (JsPath \ "hint").write[String] and
//           (JsPath \ "type").write[FieldDefinitionType] and
//           (JsPath \ "shortDescription").write[String] and
//           (JsPath \ "validation").writeNullable[ValidationGroup]

//       (if(o.access == AccessRequirements.Default) {
//         (common)(unlift(FieldDefinition.unapply).andThen(dropTail))
//       } else {
//         (common and (JsPath \ "access").write[AccessRequirements])(unlift(FieldDefinition.unapply))
//       }).writes(o)
//     }
//   }

//   implicit val ApiFieldDefinitionsJF: OFormat[ApiFieldDefinitions] = Json.format[ApiFieldDefinitions]

//   implicit val FieldDefinitionsRequestJF = Json.format[FieldDefinitionsRequest]
//   implicit val SubscriptionFieldsRequestJF = Json.format[SubscriptionFieldsRequest]

//   implicit val ApiFieldDefinitionsResponseJF = Json.format[ApiFieldDefinitionsResponse]
//   implicit val BulkApiFieldDefinitionsResponseJF = Json.format[BulkApiFieldDefinitionsResponse]
//   implicit val SubscriptionFieldsResponseJF = Json.format[SubscriptionFieldsResponse]
//   implicit val SubscriptionFieldsJF = Json.format[SubscriptionFields]

//   implicit val BulkSubscriptionFieldsResponseJF = Json.format[BulkSubscriptionFieldsResponse]

//   implicit val SubsFieldValidationResponseJF: OFormat[SubsFieldValidationResponse] = derived.withTypeTag.oformat(ShortClassName)
//   implicit val InvalidSubsFieldValidationResponseJF = Json.format[InvalidSubsFieldValidationResponse]
// }

// object JsonFormatters extends JsonFormatters
