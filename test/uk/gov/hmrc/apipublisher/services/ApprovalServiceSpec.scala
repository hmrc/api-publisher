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
import scala.concurrent.Future.successful

import utils.AsyncHmrcSpec

import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock

import uk.gov.hmrc.apipublisher.config.AppConfig
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.ApprovalState.{APPROVED, NEW}
import uk.gov.hmrc.apipublisher.models.{APIApproval, Approved, New, ServiceLocation, ServicesSearch}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository

class ApprovalServiceSpec extends AsyncHmrcSpec with FixedClock {

  trait Setup {
    val mockApiApprovalRepository = mock[APIApprovalRepository]
    val mockAppConfig             = mock[AppConfig]

    val underTest = new ApprovalService(mockApiApprovalRepository, mockAppConfig, clock)

    val gatekeeperUser = Actors.GatekeeperUser("Dave Brown")

    val unapprovedServices = Seq(
      APIApproval("employee-paye", "http://employee-paye.example.com", "employePAYE", None, Some(false)),
      APIApproval("marriageallowance", "http://employee-paye.example.com", "marriage-allowance", Some("Calculate Marriage Allowance"), Some(false))
    )

    val allServices = Seq(
      APIApproval("employee-paye", "http://employee-paye.example.com", "employePAYE", None, state = NEW),
      APIApproval("marriageallowance", "http://employee-paye.example.com", "marriage-allowance", Some("Calculate Marriage Allowance"), state = APPROVED)
    )
  }

  "The ApprovalServiceSpec" should {

    "Return a list of unapproved services" in new Setup {

      when(mockApiApprovalRepository.fetchUnapprovedServices()).thenReturn(successful(unapprovedServices))

      val result = await(underTest.fetchUnapprovedServices())

      result shouldBe unapprovedServices
      verify(mockApiApprovalRepository).fetchUnapprovedServices()
    }

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

    "Allow publication of previously unknown services when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppConfig.preventAutoDeploy).thenReturn(false)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).thenReturn(successful(apiApproval.copy(approved = Some(true))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow publication of previously disabled service when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval         = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))
      val existingApiApproval = apiApproval.copy(approved = Some(false), createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))

      when(mockAppConfig.preventAutoDeploy).thenReturn(false)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn)))
        .thenReturn(successful(apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn))
    }

    "Allow publication of previously enabled service when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval         = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))
      val existingApiApproval = apiApproval.copy(approved = Some(true), createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))

      when(mockAppConfig.preventAutoDeploy).thenReturn(false)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn)))
        .thenReturn(successful(apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn))
    }

    "Prevent publication of previously unknown services when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppConfig.preventAutoDeploy).thenReturn(true)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(false)))).thenReturn(successful(apiApproval.copy(approved = Some(false))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(false)))
    }

    "Prevent publication of previously disabled service when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval         = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))
      val existingApiApproval = apiApproval.copy(approved = Some(false), createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))

      when(mockAppConfig.preventAutoDeploy).thenReturn(true)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(false), createdOn = existingApiApproval.createdOn)))
        .thenReturn(successful(apiApproval.copy(approved = Some(false), createdOn = existingApiApproval.createdOn)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(false), createdOn = existingApiApproval.createdOn))
    }

    "Allow publication of previously enabled service when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval                 = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))
      val user: Actors.GatekeeperUser = Actors.GatekeeperUser("T T")
      val existingApiApproval         = apiApproval.copy(
        approved = Some(true),
        createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))),
        approvedBy = Some(user),
        approvedOn = Some(instant)
      )

      val expectedApproval: APIApproval = apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn, approvedOn = Some(instant), approvedBy = Some(user))
      when(mockAppConfig.preventAutoDeploy).thenReturn(true)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(expectedApproval)).thenReturn(successful(expectedApproval))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(expectedApproval)
    }

    "Allow an existing Service to be approved" in new Setup {
      val apiApproval                   = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))
      val existingApiApproval           = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))
      val expectedApproval: APIApproval =
        apiApproval.copy(approved = Some(true), createdOn = existingApiApproval.createdOn, approvedOn = Some(instant), approvedBy = Some(gatekeeperUser))

      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(existingApiApproval)))
      when(mockApiApprovalRepository.save(expectedApproval)).thenReturn(successful(expectedApproval))
      val result = await(underTest.approveService("testService", gatekeeperUser))

      result shouldBe ServiceLocation("testService", "http://localhost/myservice")
      verify(mockApiApprovalRepository).save(expectedApproval)
    }
    /*
APIApproval(testService,http://localhost/myservice,testServiceName,Some(Test Service Description),Some(true),Some(2024-10-30T15:50:15.134280169Z),Some(2020-01-02T03:04:05.006Z),Some(GatekeeperUser(Dave Brown)))
     */
    "Raise an exception if an attempt is made to approve an unknown service" in new Setup {
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.approveService("testService", gatekeeperUser))
      }
      ex.getMessage.contains("testService") shouldBe true
      verify(mockApiApprovalRepository).fetch("testService")
    }

    "Return a summary of a service when requested" in new Setup {
      val apiApproval         = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))
      val existingApiApproval = apiApproval.copy(createdOn = apiApproval.createdOn.map(_.minus(Duration.ofDays(5))))

      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(existingApiApproval)))
      val result = await(underTest.fetchServiceApproval("testService"))

      result shouldBe apiApproval.copy(createdOn = existingApiApproval.createdOn)
      verify(mockApiApprovalRepository).fetch("testService")
    }

    "Raise an exception if a summary of an unknown service when requested" in new Setup {
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      intercept[UnknownApiServiceException] {
        await(underTest.fetchServiceApproval("testService"))
      }
      verify(mockApiApprovalRepository).fetch("testService")
    }

  }

}
