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

import com.google.inject.AbstractModule
import javax.inject.Inject
import play.api.Mode.Mode
import play.api.{Application, Configuration}
import uk.gov.hmrc.apipublisher.connectors.{DocumentationRamlLoader, DocumentationUrlRewriter}
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.ramltools.loaders.{RamlLoader, UrlRewriter}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[UrlRewriter]).to(classOf[DocumentationUrlRewriter])
    bind(classOf[RamlLoader]).to(classOf[DocumentationRamlLoader])
    bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    bind(classOf[ServicesConfig]).to(classOf[DefaultServicesConfig])
  }
}

@Inject
class DefaultServicesConfig @Inject()(app: Application) extends ServicesConfig {
  override protected def mode: Mode = app.mode

  override protected def runModeConfiguration: Configuration = app.configuration
}
