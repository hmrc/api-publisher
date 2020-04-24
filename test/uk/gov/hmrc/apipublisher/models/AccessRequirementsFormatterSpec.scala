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

package uk.gov.hmrc.apipublisher.model

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.apipublisher.models.DevhubAccessLevel.{Admininstator, Developer}
import uk.gov.hmrc.apipublisher.models.AccessRequirementsFormatters
import play.api.libs.json._
import uk.gov.hmrc.apipublisher.models.DevhubAccessRequirements
import uk.gov.hmrc.apipublisher.models.DevhubAccessRequirement
import uk.gov.hmrc.apipublisher.models.DevhubAccessLevel
import uk.gov.hmrc.apipublisher.models.AccessRequirements
import uk.gov.hmrc.apipublisher.models.FieldDefinition
import uk.gov.hmrc.apipublisher.models.FieldDefinitionType

class AccessRequirementsFormatterSpec extends WordSpec with Matchers with AccessRequirementsFormatters {

  private def objectAsJsonString[A](a: A)(implicit t: Writes[A]) = Json.asciiStringify(Json.toJson(a))

  "DevhubAccessRequirements" should {
    "marshall a default correctly" in {
      val rq = DevhubAccessRequirements.Default

      Json.stringify(Json.toJson(rq)) shouldBe "{}"
    }

    "marshall a readOnly option" in {
      val rq = DevhubAccessRequirements(readOnly = DevhubAccessLevel.Admininstator)

      Json.stringify(Json.toJson(rq)) shouldBe """{"readOnly":"administrator","readWrite":"administrator"}"""
    }

    "marshall a readWrite option" in {
      val rq = DevhubAccessRequirements(readOnly = DevhubAccessRequirement.Default, readWrite = DevhubAccessRequirement.NoOne)

      Json.stringify(Json.toJson(rq)) shouldBe """{"readWrite":"noone"}"""
    }

    "marshall a complete option" in {
      val rq = DevhubAccessRequirements(readOnly = DevhubAccessLevel.Admininstator, readWrite = DevhubAccessRequirement.NoOne)

      Json.stringify(Json.toJson(rq)) shouldBe """{"readOnly":"administrator","readWrite":"noone"}"""
    }

    "unmarshall a default correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("{}")) shouldBe JsSuccess(DevhubAccessRequirements.Default)
    }

    "unmarshall a readOnly correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("""{"readOnly":"administrator"}""")) shouldBe JsSuccess(DevhubAccessRequirements(readOnly = Admininstator, readWrite = Developer))
    }

    "unmarshall a readWrite correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("""{"readWrite":"noone"}""")) shouldBe JsSuccess(DevhubAccessRequirements(readOnly = Developer, readWrite = DevhubAccessRequirement.NoOne))
    }

    "unmarshall a complete option correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("""{"readOnly":"administrator","readWrite":"noone"}""")) shouldBe JsSuccess(DevhubAccessRequirements(readOnly = Admininstator, readWrite = DevhubAccessRequirement.NoOne))
    }
  }

  "AccessRequirements" should {
    "marshalling a default correctly" in {
      val rq = AccessRequirements.Default

      Json.stringify(Json.toJson(rq)) shouldBe """{"devhub":{}}"""
    }

    "marshalling with some devhub requirements correctly" in {
      // readOnly is set explicity, but readWrite will be given this greater restriction too.
      val rq = AccessRequirements(devhub = DevhubAccessRequirements.apply(readOnly = Admininstator))

      Json.stringify(Json.toJson(rq)) shouldBe """{"devhub":{"readOnly":"administrator","readWrite":"administrator"}}"""
    }

    "unmarshall with default correctly" in {
      Json.fromJson[AccessRequirements](Json.parse("""{"devhub":{}}""")) shouldBe JsSuccess(AccessRequirements.Default)
    }

    "unmarshall with non default correctly" in {
      Json.fromJson[AccessRequirements](Json.parse("""{"devhub":{"readOnly":"administrator"}}""")) shouldBe JsSuccess(AccessRequirements(devhub = DevhubAccessRequirements(readOnly = Admininstator)))
    }
  }

  "FieldDefinition" should {
    val FakeFieldDefinitionWithAccess: FieldDefinition = FieldDefinition(name = "name", description= "description", 
                                      hint=Some("hint"), `type` = FieldDefinitionType.STRING, shortDescription = Some("shortDescription"), validation = None, 
                                      access = AccessRequirements(devhub = DevhubAccessRequirements(readOnly = Admininstator)))

    "marshal json with non default access" in {
      objectAsJsonString(FakeFieldDefinitionWithAccess) should include(""","access":{"devhub":{"readOnly":"administrator","readWrite":"administrator"}}""")
    }

    "marshal json without mention of default access" in {
      objectAsJsonString(FakeFieldDefinitionWithAccess.copy(access = AccessRequirements.Default)) should not include(""""access":{"devhub":{"readOnly":"administrator", "readWrite":"administrator"}}""")
      objectAsJsonString(FakeFieldDefinitionWithAccess.copy(access = AccessRequirements.Default)) should not include(""""access"""")
    }
  }
}
