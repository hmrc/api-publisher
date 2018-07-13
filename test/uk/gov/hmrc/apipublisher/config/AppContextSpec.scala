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

package uk.gov.hmrc.apipublisher.config

import org.scalatestplus.play.MixedPlaySpec
import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder

class AppContextSpec extends MixedPlaySpec {

  "AppContext" must {

    "Correctly rewrite URLs for the STUB environment" in new App(GuiceApplicationBuilder().configure("run.mode" -> "Stub").in(Mode.Prod).build()) {
      val appContext = new AppContext(app.configuration)

      appContext.ramlLoaderRewrites("https://developer.service.hmrc.gov.uk") mustBe "http://localhost:9680"
    }

    "Correctly rewrite URLs for the DEV environment" in new App(GuiceApplicationBuilder().configure("run.mode" -> "Dev").in(Mode.Prod).build()) {
      val appContext = new AppContext(app.configuration)

      appContext.ramlLoaderRewrites("https://developer.service.hmrc.gov.uk") mustBe "http://localhost:9680"
    }

    "Correctly rewrite URLs for the TEST environment" in new App(GuiceApplicationBuilder().configure("run.mode" -> "Test").in(Mode.Prod).build()) {
      val appContext = new AppContext(app.configuration)

      appContext.ramlLoaderRewrites("https://developer.service.hmrc.gov.uk") mustBe "http://localhost:9680"
    }

  }

}
