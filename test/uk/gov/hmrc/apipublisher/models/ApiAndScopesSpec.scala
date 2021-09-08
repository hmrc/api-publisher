/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.apipublisher.models
import uk.gov.hmrc.http.UnprocessableEntityException
import utils.AsyncHmrcSpec


class ApiAndScopesSpec extends AsyncHmrcSpec {

  "ValidateAPIScopesAreDefined" should {

    "pass when the scopes used in the API are defined" in {
      noException should be thrownBy apiAndScope("/input/api-definition-with-endpoints.json").validateAPIScopesAreDefined()
    }

    "fail when the scopes used in the API are not defined" in {
      an [UnprocessableEntityException] should be thrownBy apiAndScope("/input/api-definition-invalid-scope.json").validateAPIScopesAreDefined()
    }
  }

  "API Name should be extracted from the JSON definition" in {
    apiAndScope("/input/api-definition-with-endpoints.json").apiName shouldEqual "Test"
  }

  "API Description should be extracted from the JSON definition" in {
    apiAndScope("/input/api-definition-with-endpoints.json").description.get shouldEqual "Test API"
  }

  "API version numbers should be extracted from the JSON definition" in {
    apiAndScope("/input/api-definition-with-endpoints.json").versionNumbers should contain only("1.0", "2.0", "3.0")
  }

  "Field definitions should be extracted from the JSON definition" in {
    val apiAndScopes = ApiAndScopes(api = json("/input/api-with-endpoints-and-fields.json").as[JsObject], scopes = Some(JsArray()))
    val apiContext = apiAndScopes.apiContext
    val expectedApiWithoutFieldDefinitions = json("/input/api-with-endpoints.json").as[JsObject]
    val expectedApiFieldDefinitions: Seq[ApiFieldDefinitions] = Seq(
      models.ApiFieldDefinitions(apiContext, "1.0", (json("/input/field-definitions_1.json") \ "fieldDefinitions").as[Seq[FieldDefinition]]),
      models.ApiFieldDefinitions(apiContext, "2.0", (json("/input/field-definitions_2.json") \ "fieldDefinitions").as[Seq[FieldDefinition]]),
      models.ApiFieldDefinitions(apiContext, "2.1", (json("/input/field-definitions_2.1.json") \ "fieldDefinitions").as[Seq[FieldDefinition]]))

    apiAndScopes.fieldDefinitions shouldBe expectedApiFieldDefinitions
    apiAndScopes.apiWithoutFieldDefinitions shouldBe expectedApiWithoutFieldDefinitions
  }

  private def apiAndScope(resource: String) = json(resource).as[ApiAndScopes]

  private def json(resource: String) = Json.parse(getClass.getResourceAsStream(resource))
}
