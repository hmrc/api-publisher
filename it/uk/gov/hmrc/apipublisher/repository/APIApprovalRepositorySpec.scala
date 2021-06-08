/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.apipublisher.repository

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apipublisher.models.APIApproval
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import utils.AsyncHmrcSpec


import scala.concurrent.ExecutionContext.Implicits.global

class APIApprovalRepositorySpec extends AsyncHmrcSpec
  with MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll {

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val repository = new APIApprovalRepository(reactiveMongoComponent)

  override def beforeEach() {
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override protected def afterAll() {
    await(repository.drop)
  }

  "createOrUpdate" should {

    "create a new API Approval in Mongo and fetch that same API Definition" in {
      val apiApproval = APIApproval("testService", "http://localhost:9000/testService", "MyTestService", Some("Dummy Service created for Integration Tests"))
      await(repository.save(apiApproval))

      val result = await(repository.fetch(apiApproval.serviceName)).get

      result shouldBe apiApproval
      result.isApproved shouldEqual false
    }

    "update an existing API Approval in Mongo and fetch that same API Definition" in {

      val apiApproval = APIApproval("testService", "http://localhost:9000/testService", "MyTestService", Some("Dummy Service created for Integration Tests"))
      await(repository.save(apiApproval))

      // Update and Approve Service
      val updatedAPIApproval = APIApproval("testService", "http://localhost:9000/updatedService", "MyUpdatedService", Some("Updated description"), Some(true))
      await(repository.save(updatedAPIApproval))

      val result = await(repository.fetch(apiApproval.serviceName)).get

      result shouldBe updatedAPIApproval
      result.isApproved shouldEqual true
    }

  }

  "fetchUnApproved" should {

    "return a list containing only services which are marked as Unapproved" in {
      val apiApproval1 = APIApproval("calendar", "http://calendar", "Calendar API", Some("My Calendar API"))
      val apiApproval2 = APIApproval("employment", "http://employment", "Employment API", Some("Employment API"), Some(true))
      val apiApproval3 = APIApproval("marriage", "http://marriage", "Marriage Allowance API", Some("Marriage Allowance API"), Some(false))
      val apiApproval4 = APIApproval("retirement", "http://retirement", "Retirement API", Some("Retirement API"), Some(true))

      await(repository.save(apiApproval1))
      await(repository.save(apiApproval2))
      await(repository.save(apiApproval3))
      await(repository.save(apiApproval4))

      val result = await(repository.fetchUnapprovedServices())

      result.size shouldBe 2
      result.contains(apiApproval1.copy(approved = Some(false))) shouldBe true
      result.contains(apiApproval3) shouldBe true
    }

    "return an empty list is there are no unapproved services" in {
      val apiApproval1 = APIApproval("calendar", "http://calendar", "Calendar API", Some("My Calendar API"), Some(true))
      val apiApproval2 = APIApproval("employment", "http://employment", "Employment API", Some("Employment API"), Some(true))
      val apiApproval3 = APIApproval("marriage", "http://marriage", "Marriage Allowance API", Some("Marriage Allowance API"), Some(true))
      val apiApproval4 = APIApproval("retirement", "http://retirement", "Retirement API", Some("Retirement API"), Some(true))

      await(repository.save(apiApproval1))
      await(repository.save(apiApproval2))
      await(repository.save(apiApproval3))
      await(repository.save(apiApproval4))

      val result = await(repository.fetchUnapprovedServices())

      result.size shouldBe 0
    }
  }

}
