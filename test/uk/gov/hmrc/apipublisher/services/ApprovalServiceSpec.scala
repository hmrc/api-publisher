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

package uk.gov.hmrc.apipublisher.services

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import utils.AsyncHmrcSpec

import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock

import uk.gov.hmrc.apipublisher.config.AppConfig
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.ApprovalStatus.{APPROVED, FAILED, NEW, RESUBMITTED}
import uk.gov.hmrc.apipublisher.models._
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository

class ApprovalServiceSpec extends AsyncHmrcSpec with FixedClock {

  trait Setup {
    val mockApiApprovalRepository = mock[APIApprovalRepository]
    val mockAppConfig             = mock[AppConfig]

    val underTest = new ApprovalService(mockApiApprovalRepository, mockAppConfig, clock)

    val gatekeeperUser = Actors.GatekeeperUser("Dave Brown")

    val unapprovedServices = Seq(
      APIApproval("employee-paye", "http://employee-paye.example.com", "employePAYE", None, status = NEW),
      APIApproval("marriageallowance", "http://employee-paye.example.com", "marriage-allowance", Some("Calculate Marriage Allowance"), status = NEW)
    )

    val allServices = Seq(
      APIApproval("employee-paye", "http://employee-paye.example.com", "employePAYE", None, status = NEW),
      APIApproval("marriageallowance", "http://employee-paye.example.com", "marriage-allowance", Some("Calculate Marriage Allowance"), status = APPROVED)
    )

    val serviceName      = "testService"
    val approvalNotes    = Some("Good for approval")
    val declineNotes     = Some("Failed")
    val notes            = Some("New note")
    val processActor     = Actors.Process("Publish process")
    val newState         = ApiApprovalState(status = Some(ApprovalStatus.NEW), actor = processActor, notes = Some("Publish process"), changedAt = instant.minus(Duration.ofDays(5)))
    val approvedState    = ApiApprovalState(actor = gatekeeperUser, changedAt = instant, status = Some(APPROVED), notes = approvalNotes)
    val failedState      = ApiApprovalState(actor = gatekeeperUser, changedAt = instant, status = Some(FAILED), notes = declineNotes)
    val resubmittedState = ApiApprovalState(actor = processActor, notes = Some("Publish process"), changedAt = instant, status = Some(RESUBMITTED))
    val apiApproval      = APIApproval(serviceName, "http://localhost/myservice", "testServiceName", Some("Test Service Description"), stateHistory = Seq(newState))
  }

  "The ApprovalServiceSpec" should {

    "Return a list of all services" in new Setup {

      when(mockApiApprovalRepository.fetchAllServices()).thenReturn(successful(allServices))

      val result = await(underTest.fetchAllServices())

      result shouldBe allServices
      verify(mockApiApprovalRepository).fetchAllServices()
    }

    "Return a list of services when searching" in new Setup {

      when(mockApiApprovalRepository.searchServices(*)).thenReturn(successful(allServices))

      val result = await(underTest.searchServices(new ServicesSearch(List(New, Approved))))

      result shouldBe allServices
      verify(mockApiApprovalRepository).searchServices(new ServicesSearch(List(New, Approved)))
    }

    "Create ApiApproval with status NEW for previously unknown services" in new Setup {
      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(None))
      when(mockApiApprovalRepository.save(apiApproval.copy(status = NEW))).thenReturn(successful(apiApproval.copy(status = NEW)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(apiApproval.copy(status = NEW))
    }

    "Not change API Approval with NEW status" in new Setup {
      val existingApiApproval = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(existingApiApproval))
        .thenReturn(successful(existingApiApproval))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(existingApiApproval)
    }

    "Allow publication of previously approved service and not change the API Approval" in new Setup {
      val user: Actors.GatekeeperUser = Actors.GatekeeperUser("T T")
      val existingApiApproval         = apiApproval.copy(
        status = APPROVED,
        createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))),
        approvedBy = Some(user),
        approvedOn = Some(instant),
        stateHistory = apiApproval.stateHistory :+ approvedState
      )

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(*)).thenReturn(successful(existingApiApproval))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(existingApiApproval)
    }

    "Allow publication of previously failed service" in new Setup {
      val existingApiApproval = apiApproval.copy(
        status = FAILED,
        createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))),
        stateHistory = apiApproval.stateHistory :+ failedState
      )

      val expectedApproval: APIApproval = existingApiApproval.copy(
        status = RESUBMITTED,
        stateHistory = existingApiApproval.stateHistory :+ resubmittedState
      )

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(*)).thenReturn(successful(expectedApproval))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(expectedApproval)
    }

    "Allow an existing Service to be approved" in new Setup {
      val existingApiApproval           = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))
      val expectedApproval: APIApproval =
        apiApproval.copy(
          status = APPROVED,
          createdOn = existingApiApproval.createdOn,
          approvedOn = Some(instant),
          approvedBy = Some(gatekeeperUser),
          stateHistory = apiApproval.stateHistory :+ approvedState
        )

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(*)).thenReturn(successful(expectedApproval))
      val result = await(underTest.approveService(serviceName, gatekeeperUser, approvedState.notes))

      result shouldBe ServiceLocation(serviceName, "http://localhost/myservice")
      verify(mockApiApprovalRepository).save(expectedApproval)
    }

    "Raise an exception if an attempt is made to approve an unknown service" in new Setup {
      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(None))
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.approveService(serviceName, gatekeeperUser, approvalNotes))
      }
      ex.getMessage.contains(serviceName) shouldBe true
      verify(mockApiApprovalRepository).fetch(serviceName)
    }

    "Allow an existing Service to be declined" in new Setup {
      val existingApiApproval           = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))
      val expectedApproval: APIApproval =
        apiApproval.copy(
          status = FAILED,
          createdOn = existingApiApproval.createdOn,
          approvedOn = None,
          approvedBy = None,
          stateHistory = apiApproval.stateHistory :+ failedState
        )

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(*)).thenReturn(successful(expectedApproval))
      val result = await(underTest.declineService(serviceName, gatekeeperUser, declineNotes))

      result shouldBe ServiceLocation(serviceName, "http://localhost/myservice")
      verify(mockApiApprovalRepository).save(expectedApproval)
    }

    "Raise an exception if an attempt is made to decline an unknown service" in new Setup {
      when(mockApiApprovalRepository.fetch(*)).thenReturn(successful(None))
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.declineService(serviceName, gatekeeperUser, declineNotes))
      }
      ex.getMessage.contains(serviceName) shouldBe true
      verify(mockApiApprovalRepository).fetch(serviceName)
    }

    "Add a comment to an existing Service" in new Setup {
      val existingApiApproval           = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))
      val expectedApproval: APIApproval =
        existingApiApproval.copy(
          stateHistory = existingApiApproval.stateHistory :+ existingApiApproval.stateHistory.head.copy(
            actor = gatekeeperUser,
            changedAt = instant,
            notes = notes,
            status = None
          )
        )

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(*)).thenReturn(successful(expectedApproval))
      val result = await(underTest.addComment(serviceName, gatekeeperUser, notes))

      result shouldBe ServiceLocation(serviceName, "http://localhost/myservice")
      verify(mockApiApprovalRepository).save(expectedApproval)
    }

    "Raise an exception if an attempt is made to add a comment to an unknown service" in new Setup {
      when(mockApiApprovalRepository.fetch(*)).thenReturn(successful(None))
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.addComment(serviceName, gatekeeperUser, notes))
      }
      ex.getMessage.contains(serviceName) shouldBe true
      verify(mockApiApprovalRepository).fetch(serviceName)
    }

    "Return a summary of a service when requested" in new Setup {
      val existingApiApproval = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))

      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(Some(existingApiApproval)))
      val result = await(underTest.fetchServiceApproval(serviceName))

      result shouldBe apiApproval.copy(createdOn = existingApiApproval.createdOn)
      verify(mockApiApprovalRepository).fetch(serviceName)
    }

    "Raise an exception if a summary of an unknown service when requested" in new Setup {
      when(mockApiApprovalRepository.fetch(serviceName)).thenReturn(successful(None))
      intercept[UnknownApiServiceException] {
        await(underTest.fetchServiceApproval(serviceName))
      }
      verify(mockApiApprovalRepository).fetch(serviceName)
    }

    "return success when deleting an ApiApproval" in new Setup {
      when(mockApiApprovalRepository.delete(*)).thenReturn(successful())
      await(underTest.deleteApiApproval(serviceName))

      verify(mockApiApprovalRepository).delete(serviceName)
    }

    "fail when repository delete fails" in new Setup {
      when(mockApiApprovalRepository.delete(serviceName)).thenReturn(failed(new RuntimeException()))

      intercept[RuntimeException] {
        await(underTest.deleteApiApproval(serviceName))
      }
    }
  }
}
