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

import scala.io.Source

import play.api.libs.json.*

enum APICategory {

  case Example, Agents, BusinessRates, Charities, ConstructionIndustryScheme, CorporationTax, Customs, Estates, HelpToSave, IncomeTaxMtd, LifetimeIsa, MarriageAllowance,
    NationalInsurance, Paye, Pensions, PrivateGovernment, ReliefAtSource, SelfAssessment, StampDuty, Trusts, Vat, VatMtd, Other

}

object APICategory {
  def apply(text: String): Option[APICategory] = APICategory.values.find(_.toString().equalsIgnoreCase(text))

  def unsafeApply(text: String): APICategory = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid API Category"))

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SimpleEnumJsonFormatting
  given Format[APICategory] = SimpleEnumJsonFormatting.screamingSnakeCaseFormatFor[APICategory]("API Category", apply)

  val categoryMap: Map[String, Seq[APICategory]] =
    Json.parse(Source.fromInputStream(getClass.getResourceAsStream("/categories.json")).mkString).as[Map[String, Seq[APICategory]]]
}
