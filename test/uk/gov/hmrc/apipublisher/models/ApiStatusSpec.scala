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

import utils.HmrcSpec

import play.api.libs.json.{JsResultException, Json}

class ApiStatusSpec extends HmrcSpec {

  "Parsing an ApiStatus" should {
    "parse STABLE correctly" in {
      val partialApiVersion = parseStatus("STABLE")
      partialApiVersion.status shouldBe ApiStatus.STABLE
    }

    "parse BETA correctly" in {
      val partialApiVersion = parseStatus("BETA")
      partialApiVersion.status shouldBe ApiStatus.BETA
    }

    "convert PUBLISHED to STABLE" in {
      val partialApiVersion = parseStatus("PUBLISHED")
      partialApiVersion.status shouldBe ApiStatus.STABLE
    }

    "convert PROTOTYPED to BETA" in {
      val partialApiVersion = parseStatus("PROTOTYPED")
      partialApiVersion.status shouldBe ApiStatus.BETA
    }

    "thrown an exception if the status is INVALID" in {
      val exception = intercept[JsResultException](parseStatus("INVALID"))
      exception.getMessage should include("INVALID is not a status")
    }
  }

  private def parseStatus(status: String) = {
    Json.parse(s"""{"status":"$status","version":"1.0","endpointsEnabled":true}""").as[PartialApiVersion]
  }
}
