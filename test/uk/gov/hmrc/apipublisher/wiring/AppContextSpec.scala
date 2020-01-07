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

package uk.gov.hmrc.apipublisher.wiring

import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.play.test.UnitSpec

class AppContextSpec extends UnitSpec with MockitoSugar {

  "AppContext" must {
    "Correctly rewrite URLs for an environment" in {
      val mockConfiguration = mock[Configuration]
      val mockEnvironment = mock[Environment]

      when(mockConfiguration.getString(s"Test.ramlLoaderUrlRewrite.from")).thenReturn(Option("mockFrom"))
      when(mockConfiguration.getString(s"Test.ramlLoaderUrlRewrite.to")).thenReturn(Option("moTo"))

      val appContext = new AppContext(mockConfiguration, mockEnvironment) {
        override protected def mode: Mode = Mode.Test
      }

      appContext.ramlLoaderRewrites("mockFrom") shouldBe "moTo"
    }
  }
}
