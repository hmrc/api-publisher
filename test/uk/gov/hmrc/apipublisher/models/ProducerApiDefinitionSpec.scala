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

import utils.AsyncHmrcSpec

import play.api.libs.json._

import uk.gov.hmrc.apipublisher.models

class ProducerApiDefinitionSpec extends AsyncHmrcSpec {

  "API Name should be extracted from the JSON definition" in {
    producerApiDefinition("/input/valid-api-definition.json").apiName shouldEqual "Test"
  }

  "API Description should be extracted from the JSON definition" in {
    producerApiDefinition("/input/valid-api-definition.json").description.get shouldEqual "Test API"
  }

  "API version numbers should be extracted from the JSON definition" in {
    producerApiDefinition("/input/valid-api-definition.json").versionNumbers should contain.only("1.0", "2.0", "3.0")
  }

  "Field definitions should be extracted from the JSON definition" in {
    val producerApiDefinition                                 = ProducerApiDefinition(api = json("/input/api-with-endpoints-and-fields.json").as[JsObject])
    val apiContext                                            = producerApiDefinition.apiContext
    val expectedApiWithoutFieldDefinitions                    = json("/input/valid-api.json").as[JsObject]
    val expectedApiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
      models.ApiFieldDefinitions(apiContext, "1.0", (json("/input/field-definitions_1.json") \ "fieldDefinitions").as[Seq[FieldDefinition]]),
      models.ApiFieldDefinitions(apiContext, "2.0", (json("/input/field-definitions_2.json") \ "fieldDefinitions").as[Seq[FieldDefinition]])
    )

    producerApiDefinition.fieldDefinitions shouldBe expectedApiFieldDefinitions
    producerApiDefinition.apiWithoutFieldDefinitions shouldBe expectedApiWithoutFieldDefinitions
  }

  private def producerApiDefinition(resource: String) = json(resource).as[ProducerApiDefinition]

  private def json(resource: String) = Json.parse(getClass.getResourceAsStream(resource))
}
