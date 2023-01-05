/*
 * Copyright 2022 HM Revenue & Customs
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

import scala.io.Source

object APICategory extends Enumeration {
  type APICategory = Value

  val EXAMPLE, AGENTS, BUSINESS_RATES, CHARITIES, CONSTRUCTION_INDUSTRY_SCHEME, CORPORATION_TAX, CUSTOMS, ESTATES, HELP_TO_SAVE, INCOME_TAX_MTD,
  LIFETIME_ISA, MARRIAGE_ALLOWANCE, NATIONAL_INSURANCE, PAYE, PENSIONS, PRIVATE_GOVERNMENT,
  RELIEF_AT_SOURCE, SELF_ASSESSMENT, STAMP_DUTY, TRUSTS, VAT, VAT_MTD, OTHER = Value

  implicit val formatAPICategory: Format[APICategory] = Format(Reads.enumNameReads(APICategory), Writes.enumNameWrites[APICategory.type])

  val categoryMap: Map[String, Seq[APICategory]] =
    Json.parse(Source.fromInputStream(getClass.getResourceAsStream("/categories.json")).mkString).as[Map[String, Seq[APICategory]]]
}
