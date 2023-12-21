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

package uk.gov.hmrc.apipublisher.connectors

import scala.concurrent.Future.{failed, successful}

import io.swagger.v3.oas.models.OpenAPI
import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apipublisher.models.ServiceLocation

import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, DefinitionFileNoBodyReturned}

trait MicroserviceConnectorMockModule {
  self: MockitoSugar with ArgumentMatchersSugar =>

  trait BaseMicroserviceConnectorMock {
    def aMock: MicroserviceConnector

    object GetAPIAndScopes {

      def findsNone =
        when(aMock.getAPIAndScopes(*)(*)).thenReturn(successful(Left(DefinitionFileNoBodyReturned(ServiceLocation("", "")))))

      def fails = {
        val errorMessage = "something went wrong"
        when(aMock.getAPIAndScopes(*)(*)).thenReturn(failed(new RuntimeException(errorMessage)))
      }

      def returns(in: ApiAndScopes) = {
        when(aMock.getAPIAndScopes(*)(*)).thenReturn(successful(Right(in)))
      }
    }

    object GetOAS {

      def findsNoValidFile =
        when(aMock.getOAS(*, *)).thenReturn(failed(new IllegalArgumentException("Cannot find valid OAS file")))

      def findsValid(openAPI: OpenAPI) =
        when(aMock.getOAS(*, *)).thenReturn(successful(openAPI))
    }
  }

  object MicroserviceConnectorMock extends BaseMicroserviceConnectorMock {
    val aMock = mock[MicroserviceConnector](withSettings.strictness(Strictness.LENIENT))
  }
}
