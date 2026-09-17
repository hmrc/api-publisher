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

package uk.gov.hmrc.apipublisher.model

import play.api.libs.json.*
import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

import uk.gov.hmrc.apipublisher.models.DevhubAccessLevel.*
import uk.gov.hmrc.apipublisher.models.DevhubAccessRequirement.*
import uk.gov.hmrc.apipublisher.models.Validation.given
import uk.gov.hmrc.apipublisher.models.{DevhubAccessRequirement, DevhubAccessRequirements, RegexValidationRule, UrlValidationRule, ValidationRule}

class ValidationRuleSpec extends BaseJsonFormattersSpec {
  "RegexValidationRule" should {
    val regex = "^https:"

    "read JSON correctly" in {
      testFromJson(s"""{"regex": "$regex"}""")(RegexValidationRule(regex))
    }

    "write JSON correctly" in {
      testToJson(RegexValidationRule(regex))("regex" -> regex)
    }
  }

  "UrlValidationRule" should {
    "read JSON correctly" in {
      testFromJson("{}")(UrlValidationRule)
    }
    "write JSON correctly" in {
      Json.toJson[UrlValidationRule.type](UrlValidationRule) shouldBe JsObject(Seq.empty)
    }
  }

  "ValidationRule" when {
    "RegexValidationRule" should {
      val regex = "^https:"

      "read JSON correctly" in {
        testFromJson[ValidationRule](s"""{"RegexValidationRule": {"regex": "$regex"}}""")(RegexValidationRule(regex))
      }

      "write JSON correctly" in {
        testToJsonValues[ValidationRule](RegexValidationRule(regex))("RegexValidationRule" -> JsObject(Seq(("regex" -> JsString(regex)))))
      }
    }

    "UrlValidationRule" should {
      "read JSON correctly" in {
        testFromJson[ValidationRule]("""{"UrlValidationRule":{}}""")(UrlValidationRule)
      }
      "write JSON correctly" in {
        testToJsonValues[ValidationRule](UrlValidationRule)("UrlValidationRule" -> JsObject(Seq.empty))
      }
    }
  }
}
