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

import play.api.libs.json._
import DevhubAccessLevel._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.functional.syntax._

trait AccessRequirementsFormatters {

  def ignoreDefaultField[T](value: T, default: T, jsonFieldName: String)(implicit w: Writes[T]) =
    if(value == default) None else Some((jsonFieldName, Json.toJsFieldJsValueWrapper(value)))

  implicit val DevhubAccessRequirementFormat: Format[DevhubAccessRequirement] = new Format[DevhubAccessRequirement] {

    override def writes(o: DevhubAccessRequirement): JsValue = JsString(o match {
      case Admininstator => "administrator"
      case Developer => "developer"
      case DevhubAccessRequirement.NoOne => "noone"
    })

    override def reads(json: JsValue): JsResult[DevhubAccessRequirement] = json match {
      case JsString("administrator") => JsSuccess(Admininstator)
      case JsString("developerdeveloper") => JsSuccess(Developer)
      case JsString("noone") => JsSuccess(DevhubAccessRequirement.NoOne)
      case _ => JsError("Not a recognized DevhubAccessRequirement")
    }
  }

  implicit val DevhubAccessRequirementsReads: Reads[DevhubAccessRequirements] = (
    ((JsPath \ "readOnly").read[DevhubAccessRequirement] or Reads.pure(DevhubAccessRequirement.Default)) and
    ((JsPath \ "readWrite").read[DevhubAccessRequirement] or Reads.pure(DevhubAccessRequirement.Default))
  )(DevhubAccessRequirements.apply _)

  implicit val DevhubAccessRequirementsWrites: OWrites[DevhubAccessRequirements] = new OWrites[DevhubAccessRequirements] {
    def writes(requirements: DevhubAccessRequirements) = {
      Json.obj(
        (
          ignoreDefaultField(requirements.readOnly, DevhubAccessRequirement.Default, "readOnly") ::
          ignoreDefaultField(requirements.readWrite, DevhubAccessRequirement.Default, "readWrite") ::
          List.empty[Option[(String, JsValueWrapper)]]
        ).filterNot(_.isEmpty).map(_.get): _*
      )
    }
  }

  implicit val AccessRequirementsReads: Reads[AccessRequirements] = Json.reads[AccessRequirements]

  implicit val AccessRequirementsWrites: Writes[AccessRequirements] = Json.writes[AccessRequirements]
}

object AccessRequirementsFormatters extends AccessRequirementsFormatters