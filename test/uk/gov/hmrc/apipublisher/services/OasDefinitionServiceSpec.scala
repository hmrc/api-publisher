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

import utils.AsyncHmrcSpec
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apipublisher.models.ServiceLocation
import uk.gov.hmrc.http.HeaderCarrier
import play.api.libs.json._
import uk.gov.hmrc.apipublisher.models.ApiAndScopes

class OasDefinitionServiceSpec extends AsyncHmrcSpec {
  val aServiceLocation = ServiceLocation("test", "http://test.example.com", Some(Map("third-party-api" -> "true")))
  implicit val hc = HeaderCarrier()

  trait MicroserviceConnectorMockModule {
    self : MockitoSugar with ArgumentMatchersSugar  =>

    trait BaseMicroserviceConnectorMock {
      def aMock: MicroserviceConnector

      object GetAPIAndScopes {
        def findsNone = 
          when(aMock.getAPIAndScopes(*)(*)).thenReturn(successful(None))

        def fails = {
          val errorMessage = "something went wrong"
          when(aMock.getAPIAndScopes(*)(*)).thenReturn(failed(new RuntimeException(errorMessage)))
        }
        
        def returns(in: ApiAndScopes) = {
          when(aMock.getAPIAndScopes(*)(*)).thenReturn(successful(Some(in)))
        }
      }
    }  

    object MicroserviceConnectorMock extends BaseMicroserviceConnectorMock {
      val aMock = mock[MicroserviceConnector](withSettings.lenient())
    }
  }

  trait Setup 
      extends MicroserviceConnectorMockModule 
      with MockitoSugar 
      with ArgumentMatchersSugar {

    def json[J <: JsValue](path: String)(implicit fjs: Reads[J]): J = Json.parse(getClass.getResourceAsStream(path)).as[J]

    val service = new OasDefinitionService(MicroserviceConnectorMock.aMock)
  }

  "OasDefinitionService" should {
    "return successful None if microservice has no OAS" in new Setup {
      MicroserviceConnectorMock.GetAPIAndScopes.findsNone
      
      val result = await(service.getDefinition(aServiceLocation))
      
      result shouldBe None
    }

    "fail if the microservice call fails" in new Setup {
      MicroserviceConnectorMock.GetAPIAndScopes.fails
      
      intercept[RuntimeException] {
        await(service.getDefinition(aServiceLocation))
      }.getMessage shouldBe "something went wrong"
    }
    
    "returns an api and scopes" in new Setup {
      val api = json[JsObject]("/input/api_no_endpoints_one_version.json")
      val scopes = json[JsArray]("/input/scopes.json")
      
      MicroserviceConnectorMock.GetAPIAndScopes.returns(ApiAndScopes(api, scopes))

      val result = await(service.getDefinition(aServiceLocation))

      result shouldBe 'defined
      result.value shouldBe ApiAndScopes(JsObject(Seq.empty), JsArray.empty)
    }
  }
}
