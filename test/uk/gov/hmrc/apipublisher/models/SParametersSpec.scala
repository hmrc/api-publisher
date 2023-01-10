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

import io.swagger.v3.oas.models.parameters._
import utils.HmrcSpec

import uk.gov.hmrc.apipublisher.models.oas._

class SParametersSpec extends HmrcSpec {

  def process(p: Parameter): (String, String) = {
    SParameters.apply(List(p)).map(tuple => tuple._1).map(paramKey => (paramKey.name.value, paramKey.in.value)).head
  }

  "SParametersSpec" should {
    val paramName = "myName"

    "fail on a require for an generic parameter with no `in`" in {
      intercept[IllegalArgumentException] {
        process(new Parameter().in(null).name(paramName))
      }.getMessage() should include("(NULL)")
    }

    "fail on a require for an generic parameter with invalid `in`" in {
      intercept[IllegalArgumentException] {
        process(new Parameter().in("bobbins").name(paramName))
      }.getMessage() should include("bobbins")
    }
  }
}
