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

import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec

import uk.gov.hmrc.apipublisher.models.PublisherApiStatus

class PublisherApiStatusSpec extends HmrcSpec {

  "Parsing an ApiStatus" should {
    "parse STABLE correctly" in {
      val status = parseStatus("STABLE")
      status shouldBe PublisherApiStatus.Stable
    }

    "parse BETA correctly" in {
      val status = parseStatus("BETA")
      status shouldBe PublisherApiStatus.Beta
    }

    "convert PUBLISHED to STABLE" in {
      val status = parseStatus("PUBLISHED")
      status shouldBe PublisherApiStatus.Stable
    }

    "convert PROTOTYPED to BETA" in {
      val status = parseStatus("PROTOTYPED")
      status shouldBe PublisherApiStatus.Beta
    }

    "thrown an exception if the status is INVALID" in {
      val exception = intercept[JsResultException](parseStatus("INVALID"))
      exception.getMessage should include("INVALID is not a valid PublisherApiStatus")
    }
  }

  private def parseStatus(status: String) = {
    Json.parse(s"""{"status":"$status","version":"1.0"}""")
      .as[PublisherApiVersion]
      .status
  }
}
