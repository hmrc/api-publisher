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

import com.google.inject.AbstractModule
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension

import uk.gov.hmrc.apipublisher.connectors.OASFileLoader.{MicroserviceOASFileLocator, OASFileLocator}
import uk.gov.hmrc.apipublisher.scheduled.MigrateApprovedFlagJob
import uk.gov.hmrc.apipublisher.services.{OasParserImpl, OasVersionDefinitionService}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[SwaggerParserExtension]).toInstance(new OpenAPIV3Parser)
    bind(classOf[OASFileLocator]).toInstance(MicroserviceOASFileLocator)
    bind(classOf[OasVersionDefinitionService.OasParser]).toInstance(new OasParserImpl)
    bind(classOf[MigrateApprovedFlagJob]).asEagerSingleton()
  }
}
