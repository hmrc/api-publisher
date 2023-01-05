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

import com.google.inject.AbstractModule
import uk.gov.hmrc.apipublisher.connectors.{DocumentationRamlLoader, DocumentationUrlRewriter}
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}
import uk.gov.hmrc.ramltools.loaders.{RamlLoader, UrlRewriter}
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector._
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension
import uk.gov.hmrc.apipublisher.services.OasParserImpl
import uk.gov.hmrc.apipublisher.services.OasVersionDefinitionService

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[UrlRewriter]).to(classOf[DocumentationUrlRewriter])
    bind(classOf[RamlLoader]).to(classOf[DocumentationRamlLoader])
    bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    bind(classOf[OASFileLocator]).toInstance(MicroserviceOASFileLocator)
    bind(classOf[SwaggerParserExtension]).toInstance(new OpenAPIV3Parser)
    bind(classOf[OasVersionDefinitionService.OasParser]).toInstance(new OasParserImpl)
  }
}

