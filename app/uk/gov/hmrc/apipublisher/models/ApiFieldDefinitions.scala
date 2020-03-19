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

import play.api.libs.json.{Format, Json, Reads, Writes}
import uk.gov.hmrc.apipublisher.models.FieldDefinitionType.FieldDefinitionType

case class ApiFieldDefinitions(apiContext: String,
                               apiVersion: String,
                               fieldDefinitions: Seq[FieldDefinition])

case class FieldDefinition(name: String, description: String, hint: Option[String], `type`: FieldDefinitionType, shortDescription: Option[String] = None)

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
