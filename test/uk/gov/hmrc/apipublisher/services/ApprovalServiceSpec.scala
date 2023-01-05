/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.apipublisher.wiring.AppContext
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{APIApproval, ServiceLocation}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository
import utils.AsyncHmrcSpec
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext.Implicits.global

class ApprovalServiceSpec extends AsyncHmrcSpec {

  trait Setup {
    val mockApiApprovalRepository = mock[APIApprovalRepository]
    val mockAppContext = mock[AppContext]

    val underTest = new ApprovalService(mockApiApprovalRepository, mockAppContext)

    val unapprovedServices = Seq(APIApproval("employee-paye", "http://employee-paye.example.com", "employePAYE", None, Some(false)),
      APIApproval("marriageallowance", "http://employee-paye.example.com", "marriage-allowance", Some("Calculate Marriage Allowance"), Some(false))
    )
  }

  "The ApprovalServiceSpec" should {

    "Return a list of unapproved services" in new Setup {

      when(mockApiApprovalRepository.fetchUnapprovedServices()).thenReturn(successful(unapprovedServices))

      val result = await(underTest.fetchUnapprovedServices())

      result shouldBe unapprovedServices
      verify(mockApiApprovalRepository).fetchUnapprovedServices()
    }

    "Allow publication of previously unknown services when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppContext.preventAutoDeploy).thenReturn(false)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).thenReturn(successful(apiApproval.copy(approved = Some(true))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow publication of previously disabled service when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppContext.preventAutoDeploy).thenReturn(false)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(apiApproval.copy(approved = Some(false)))))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).thenReturn(successful(apiApproval.copy(approved = Some(true))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow publication of previously enabled service when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppContext.preventAutoDeploy).thenReturn(false)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(apiApproval.copy(approved = Some(true)))))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).thenReturn(successful(apiApproval.copy(approved = Some(true))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Prevent publication of previously unknown services when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppContext.preventAutoDeploy).thenReturn(true)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(false)))).thenReturn(successful(apiApproval.copy(approved = Some(false))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(false)))
    }

    "Prevent publication of previously disabled service when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppContext.preventAutoDeploy).thenReturn(true)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(apiApproval.copy(approved = Some(false)))))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(false)))).thenReturn(successful(apiApproval.copy(approved = Some(false))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(false)))
    }

    "Allow publication of previously enabled service when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockAppContext.preventAutoDeploy).thenReturn(true)
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(apiApproval.copy(approved = Some(true)))))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).thenReturn(successful(apiApproval.copy(approved = Some(true))))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow an existing Service to be approved" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(apiApproval)))
      when(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).thenReturn(successful(apiApproval.copy(approved = Some(true))))
      val result = await(underTest.approveService("testService"))

      result shouldBe ServiceLocation("testService", "http://localhost/myservice")
      verify(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Raise an exception if an attempt is made to approve an unknown service" in new Setup {
      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(None))
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.approveService("testService"))
      }
      ex.getMessage.contains("testService") shouldBe true
      verify(mockApiApprovalRepository).fetch("testService")
    }

    "Return a summary of a service when requested" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      when(mockApiApprovalRepository.fetch("testService")).thenReturn(successful(Some(apiApproval)))
      val result = await(underTest.fetchServiceApproval("testService"))

      result shouldBe apiApproval
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
