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
import uk.gov.hmrc.apipublisher.models.AccessRequirementsFormatters
import play.api.libs.json._
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.models.DevhubAccessRequirement._
import uk.gov.hmrc.apipublisher.models.FieldDefinition
import uk.gov.hmrc.apipublisher.models.FieldDefinitionType

class AccessRequirementsFormatterSpec extends WordSpec with Matchers with AccessRequirementsFormatters {

  private def objectAsJsonString[A](a: A)(implicit t: Writes[A]) = Json.asciiStringify(Json.toJson(a))

  "DevhubAccessRequirements" should {
    "marshall a default correctly" in {
      val rq = DevhubAccessRequirements.Default

      Json.stringify(Json.toJson(rq)) shouldBe "{}"
    }

    "marshall a read option" in {
      val rq = DevhubAccessRequirements(read = AdminOnly)

      Json.stringify(Json.toJson(rq)) shouldBe """{"read":"adminOnly","write":"adminOnly"}"""
    }

    "marshall a write option" in {
      val rq = DevhubAccessRequirements(read = DevhubAccessRequirement.Default, write = NoOne)

      Json.stringify(Json.toJson(rq)) shouldBe """{"write":"noOne"}"""
    }

    "marshall a complete option" in {
      val rq = DevhubAccessRequirements(read = AdminOnly, write = NoOne)

      Json.stringify(Json.toJson(rq)) shouldBe """{"read":"adminOnly","write":"noOne"}"""
    }

    "unmarshall a default correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("{}")) shouldBe JsSuccess(DevhubAccessRequirements.Default)
    }

    "unmarshall a read correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("""{"read":"adminOnly"}""")) shouldBe JsSuccess(DevhubAccessRequirements(read = AdminOnly, write = Anyone))
    }

    "unmarshall a write correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("""{"write":"noOne"}""")) shouldBe JsSuccess(DevhubAccessRequirements(read = Anyone, write = NoOne))
    }

    "unmarshall a complete option correctly" in {
      Json.fromJson[DevhubAccessRequirements](Json.parse("""{"read":"adminOnly","write":"noOne"}""")) shouldBe JsSuccess(DevhubAccessRequirements(read = AdminOnly, write = NoOne))
    }
  }

  "AccessRequirements" should {
    "marshalling a default correctly" in {
      val rq = AccessRequirements.Default

      Json.stringify(Json.toJson(rq)) shouldBe """{"devhub":{}}"""
    }

    "marshalling with some devhub requirements correctly" in {
      // read is set explicity, but write will be given this greater restriction too.
      val rq = AccessRequirements(devhub = DevhubAccessRequirements.apply(read = AdminOnly))

      Json.stringify(Json.toJson(rq)) shouldBe """{"devhub":{"read":"adminOnly","write":"adminOnly"}}"""
    }

    "unmarshall with default correctly" in {
      Json.fromJson[AccessRequirements](Json.parse("""{"devhub":{}}""")) shouldBe JsSuccess(AccessRequirements.Default)
    }

    "unmarshall with non default correctly" in {
      Json.fromJson[AccessRequirements](Json.parse("""{"devhub":{"read":"adminOnly"}}""")) shouldBe JsSuccess(AccessRequirements(devhub = DevhubAccessRequirements(read = AdminOnly)))
    }
  }

  "FieldDefinition" should {
    val FakeFieldDefinitionWithAccess: FieldDefinition = FieldDefinition(name = "name", description= "description",
                                      hint=Some("hint"), `type` = FieldDefinitionType.STRING, shortDescription = Some("shortDescription"), validation = None,
                                      access = AccessRequirements(devhub = DevhubAccessRequirements(read = AdminOnly)))

    "marshal json with non default access" in {
      objectAsJsonString(FakeFieldDefinitionWithAccess) should include(""","access":{"devhub":{"read":"adminOnly","write":"adminOnly"}}""")
    }

    "marshal json without mention of default access" in {
      objectAsJsonString(FakeFieldDefinitionWithAccess.copy(access = AccessRequirements.Default)) should not include(""""access":{"devhub":{"read":"adminOnly", "write":"adminOnly"}}""")
      objectAsJsonString(FakeFieldDefinitionWithAccess.copy(access = AccessRequirements.Default)) should not include(""""access"""")
    }
  }
}
