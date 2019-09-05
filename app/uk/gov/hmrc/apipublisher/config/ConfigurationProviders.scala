/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Provider, Singleton}
import play.api.Mode.Mode
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apipublisher.connectors._
import uk.gov.hmrc.apipublisher.controllers.DocumentationConfig
import uk.gov.hmrc.play.config.ServicesConfig

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[DocumentationConfig].toProvider[DocumentationConfigProvider],
      bind[ApiDocumentationConfig].toProvider[ApiDocumentationConfigProvider],
      bind[ApiDefinitionConfig].toProvider[ApiDefinitionConfigProvider],
      bind[ApiScopeConfig].toProvider[ApiScopeConfigProvider],
      bind[ApiSSubscriptionFieldsConfig].toProvider[ApiSSubscriptionFieldsConfigProvider],
      bind[ServiceLocatorConfig].toProvider[ServiceLocatorConfigProvider],
      bind[MicroserviceConfig].toProvider[MicroserviceConfigProvider]
    )
  }
}

@Singleton
class DocumentationConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[DocumentationConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get() = {
    val publishApiDefinition = runModeConfiguration.getBoolean("publishApiDefinition").getOrElse(false)
    val apiContext = runModeConfiguration.getString("api.context").getOrElse("api-publisher")
    val access = runModeConfiguration.getConfig(s"api.access")
    DocumentationConfig(publishApiDefinition, apiContext, access)
  }
}

@Singleton
class ApiDocumentationConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiDocumentationConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get() = {
    val serviceBaseUrl = baseUrl("api-documentation")
    ApiDocumentationConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiDefinitionConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiDefinitionConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get() = {
    val serviceBaseUrl = baseUrl("api-definition")
    ApiDefinitionConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiScopeConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiScopeConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get() = {
    val serviceBaseUrl = baseUrl("api-scope")
    ApiScopeConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiSSubscriptionFieldsConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiSSubscriptionFieldsConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get() = {
    val serviceBaseUrl = baseUrl("api-subscription-fields")
    ApiSSubscriptionFieldsConfig(serviceBaseUrl)
  }
}

@Singleton
class ServiceLocatorConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ServiceLocatorConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get() = {
    val serviceBaseUrl = baseUrl("service-locator")
    ServiceLocatorConfig(serviceBaseUrl)
  }
}

@Singleton
class MicroserviceConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[MicroserviceConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get(): MicroserviceConfig = {
    val validateApiDefinition = runModeConfiguration.getBoolean("validateApiDefinition").getOrElse(true)
    MicroserviceConfig(validateApiDefinition)
  }
}
