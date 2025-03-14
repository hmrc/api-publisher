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

package uk.gov.hmrc.apipublisher.repository

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import utils.AsyncHmrcSpec

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import uk.gov.hmrc.apipublisher.models.ApprovalState.{APPROVED, FAILED, NEW, RESUBMITTED}
import uk.gov.hmrc.apipublisher.models._

class APIApprovalRepositorySpec extends AsyncHmrcSpec
    with BeforeAndAfterEach with BeforeAndAfterAll {

  protected def appBuilder: GuiceApplicationBuilder = {
    GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false,
        "mongodb.uri" -> s"mongodb://localhost:27017/test-${this.getClass.getSimpleName}"
      )
  }
  implicit lazy val app: Application                = appBuilder.build()

  private val repository: APIApprovalRepository = app.injector.instanceOf[APIApprovalRepository]

  override def beforeEach(): Unit = {
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes())
  }

  override protected def afterAll(): Unit = {
    await(repository.collection.drop().toFuture())
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

  "fetchAll" should {

    "return a list containing all services" in {
      val apiApproval1 = APIApproval("calendar", "http://calendar", "Calendar API", Some("My Calendar API"), state = NEW)
      val apiApproval2 = APIApproval("employment", "http://employment", "Employment API", Some("Employment API"), state = FAILED)
      val apiApproval3 = APIApproval("marriage", "http://marriage", "Marriage Allowance API", Some("Marriage Allowance API"), state = APPROVED)
      val apiApproval4 = APIApproval("retirement", "http://retirement", "Retirement API", Some("Retirement API"), state = RESUBMITTED)

      await(repository.save(apiApproval1))
      await(repository.save(apiApproval2))
      await(repository.save(apiApproval3))
      await(repository.save(apiApproval4))

      val result = await(repository.fetchAllServices())

      result.size shouldBe 4
      result.contains(apiApproval1) shouldBe true
      result.contains(apiApproval2) shouldBe true
      result.contains(apiApproval3) shouldBe true
      result.contains(apiApproval4) shouldBe true
    }

    "return an empty list is there are no services" in {
      val result = await(repository.fetchAllServices())

      result.size shouldBe 0
    }
  }

  "searchServices" should {

    "return expected result of 1 for approved status search" in {
      val apiApproval1 = APIApproval("calendar", "http://calendar", "Calendar API", Some("My Calendar API"), state = NEW)
      val apiApproval2 = APIApproval("employment", "http://employment", "Employment API", Some("Employment API"), state = FAILED)
      val apiApproval3 = APIApproval("marriage", "http://marriage", "Marriage Allowance API", Some("Marriage Allowance API"), state = APPROVED)
      val apiApproval4 = APIApproval("retirement", "http://retirement", "Retirement API", Some("Retirement API"), state = RESUBMITTED)

      await(repository.save(apiApproval1))
      await(repository.save(apiApproval2))
      await(repository.save(apiApproval3))
      await(repository.save(apiApproval4))

      val filters        = List(Approved)
      val searchCriteria = ServicesSearch(filters)
      val result         = await(repository.searchServices(searchCriteria))

      result.size shouldBe 1
      result.contains(apiApproval3) shouldBe true
    }

    "return expected result of 2 for approved and failed status search" in {
      val apiApproval1 = APIApproval("calendar", "http://calendar", "Calendar API", Some("My Calendar API"), state = NEW)
      val apiApproval2 = APIApproval("employment", "http://employment", "Employment API", Some("Employment API"), state = FAILED)
      val apiApproval3 = APIApproval("marriage", "http://marriage", "Marriage Allowance API", Some("Marriage Allowance API"), state = APPROVED)
      val apiApproval4 = APIApproval("retirement", "http://retirement", "Retirement API", Some("Retirement API"), state = RESUBMITTED)

      await(repository.save(apiApproval1))
      await(repository.save(apiApproval2))
      await(repository.save(apiApproval3))
      await(repository.save(apiApproval4))

      val filters        = List(Approved, Failed)
      val searchCriteria = ServicesSearch(filters)
      val result         = await(repository.searchServices(searchCriteria))

      result.size shouldBe 2
      result.contains(apiApproval2) shouldBe true
      result.contains(apiApproval3) shouldBe true
    }

    "return expected result of 4 for all statuses search" in {
      val apiApproval1 = APIApproval("calendar", "http://calendar", "Calendar API", Some("My Calendar API"), state = NEW)
      val apiApproval2 = APIApproval("employment", "http://employment", "Employment API", Some("Employment API"), state = FAILED)
      val apiApproval3 = APIApproval("marriage", "http://marriage", "Marriage Allowance API", Some("Marriage Allowance API"), state = APPROVED)
      val apiApproval4 = APIApproval("retirement", "http://retirement", "Retirement API", Some("Retirement API"), state = RESUBMITTED)

      await(repository.save(apiApproval1))
      await(repository.save(apiApproval2))
      await(repository.save(apiApproval3))
      await(repository.save(apiApproval4))

      val filters        = List(New, Approved, Failed, Resubmitted)
      val searchCriteria = ServicesSearch(filters)
      val result         = await(repository.searchServices(searchCriteria))

      result.size shouldBe 4
      result.contains(apiApproval1) shouldBe true
      result.contains(apiApproval2) shouldBe true
      result.contains(apiApproval3) shouldBe true
      result.contains(apiApproval4) shouldBe true
    }

    "return an empty list is there are no services" in {
      val filters        = List(Approved)
      val searchCriteria = ServicesSearch(filters)
      val result         = await(repository.searchServices(searchCriteria))

      result.size shouldBe 0
    }
  }
}
