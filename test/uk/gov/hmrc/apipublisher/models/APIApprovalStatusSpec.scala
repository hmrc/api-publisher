/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.prop.TableDrivenPropertyChecks

import play.api.libs.json.*
import uk.gov.hmrc.apiplatform.modules.common.utils.{BaseJsonFormattersSpec, HmrcSpec}

class APIApprovalStatusSpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks {

  "ApprovalStatus" should {
    val values =
      Table(
        ("Enum", "text"),
        (ApprovalStatus.New, "new"),
        (ApprovalStatus.Failed, "failed"),
        (ApprovalStatus.Approved, "approved"),
        (ApprovalStatus.Resubmitted, "resubmitted")
      )

    "convert to string correctly" in {
      forAll(values) { (s, t) =>
        s.toString() shouldBe t.capitalize
      }
    }

    "convert lower case string to case object" in {
      forAll(values) { (s, t) =>
        ApprovalStatus.apply(t) shouldBe Some(s)
      }
    }

    "convert mixed case string to case object" in {
      forAll(values) { (s, t) =>
        ApprovalStatus.apply(t.toUpperCase()) shouldBe Some(s)
      }
    }

    "convert string value to None when undefined or empty" in {
      ApprovalStatus.apply("rubbish") shouldBe None
      ApprovalStatus.apply("") shouldBe None
    }

    "handle when string value is invalid" in {
      ApprovalStatus.apply("rubbish") shouldBe None
    }

    "read from Json" in {
      forAll(values) { (s, t) =>
        testFromJson[ApprovalStatus](s""" "$t" """)(s)
      }
    }

    "read with error from Json" in {
      Json.parse("123").validate[ApprovalStatus] should matchPattern {
        case JsError(_) =>
      }
    }

    "write to Json" in {
      forAll(values) { (s, t) =>
        Json.toJson[ApprovalStatus](s) shouldBe JsString(t.toUpperCase())
      }
    }
  }
}
