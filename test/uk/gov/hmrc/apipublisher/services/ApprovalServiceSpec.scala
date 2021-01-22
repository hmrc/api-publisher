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

package uk.gov.hmrc.apipublisher.services

import org.mockito.BDDMockito._
import org.mockito.Mockito.{verify => verifyMock}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apipublisher.wiring.AppContext
import uk.gov.hmrc.apipublisher.exceptions.UnknownApiServiceException
import uk.gov.hmrc.apipublisher.models.{APIApproval, ServiceLocation}
import uk.gov.hmrc.apipublisher.repository.APIApprovalRepository
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

class ApprovalServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

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

      given(mockApiApprovalRepository.fetchUnapprovedServices()).willReturn(unapprovedServices)

      val result = await(underTest.fetchUnapprovedServices())

      result shouldBe unapprovedServices
      verifyMock(mockApiApprovalRepository).fetchUnapprovedServices()
    }

    "Allow publication of previously unknown services when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockAppContext.preventAutoDeploy).willReturn(false)
      given(mockApiApprovalRepository.fetch("testService")).willReturn(None)
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).willReturn(apiApproval.copy(approved = Some(true)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow publication of previously disabled service when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockAppContext.preventAutoDeploy).willReturn(false)
      given(mockApiApprovalRepository.fetch("testService")).willReturn(Some(apiApproval.copy(approved = Some(false))))
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).willReturn(apiApproval.copy(approved = Some(true)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow publication of previously enabled service when PreventAutoDeploy is disabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockAppContext.preventAutoDeploy).willReturn(false)
      given(mockApiApprovalRepository.fetch("testService")).willReturn(Some(apiApproval.copy(approved = Some(true))))
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).willReturn(apiApproval.copy(approved = Some(true)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Prevent publication of previously unknown services when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockAppContext.preventAutoDeploy).willReturn(true)
      given(mockApiApprovalRepository.fetch("testService")).willReturn(None)
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(false)))).willReturn(apiApproval.copy(approved = Some(false)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(false)))
    }

    "Prevent publication of previously disabled service when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockAppContext.preventAutoDeploy).willReturn(true)
      given(mockApiApprovalRepository.fetch("testService")).willReturn(Some(apiApproval.copy(approved = Some(false))))
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(false)))).willReturn(apiApproval.copy(approved = Some(false)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe false
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(false)))
    }

    "Allow publication of previously enabled service when PreventAutoDeploy is enabled" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockAppContext.preventAutoDeploy).willReturn(true)
      given(mockApiApprovalRepository.fetch("testService")).willReturn(Some(apiApproval.copy(approved = Some(true))))
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).willReturn(apiApproval.copy(approved = Some(true)))

      val result = await(underTest.createOrUpdateServiceApproval(apiApproval))

      result shouldBe true
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Allow an existing Service to be approved" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockApiApprovalRepository.fetch("testService")).willReturn(Some(apiApproval))
      given(mockApiApprovalRepository.save(apiApproval.copy(approved = Some(true)))).willReturn(apiApproval.copy(approved = Some(true)))
      val result = await(underTest.approveService("testService"))

      result shouldBe ServiceLocation("testService", "http://localhost/myservice")
      verifyMock(mockApiApprovalRepository).save(apiApproval.copy(approved = Some(true)))
    }

    "Raise an exception if an attempt is made to approve an unknown service" in new Setup {
      given(mockApiApprovalRepository.fetch("testService")).willReturn(None)
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.approveService("testService"))
      }
      ex.getMessage.contains("testService") shouldBe true
      verifyMock(mockApiApprovalRepository).fetch("testService")
    }

    "Return a summary of a service when requested" in new Setup {
      val apiApproval = APIApproval("testService", "http://localhost/myservice", "testServiceName", Some("Test Service Description"))

      given(mockApiApprovalRepository.fetch("testService")).willReturn(Some(apiApproval))
      val result = await(underTest.fetchServiceApproval("testService"))

      result shouldBe apiApproval
      verifyMock(mockApiApprovalRepository).fetch("testService")
    }

    "Raise an exception if a summary of an unknown service when requested" in new Setup {
      given(mockApiApprovalRepository.fetch("testService")).willReturn(None)
      val ex = intercept[UnknownApiServiceException] {
        await(underTest.fetchServiceApproval("testService"))
      }
      verifyMock(mockApiApprovalRepository).fetch("testService")
    }

  }

}
