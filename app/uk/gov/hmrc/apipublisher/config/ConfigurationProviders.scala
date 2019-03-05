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
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apipublisher.connectors.{ApiDefinitionConfig, ApiSSubscriptionFieldsConfig, ApiScopeConfig, ServiceLocatorConfig}
import uk.gov.hmrc.apipublisher.controllers.DocumentationConfig
import uk.gov.hmrc.play.config.ServicesConfig

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[DocumentationConfig].toProvider[DocumentationConfigProvider],
      bind[ApiDefinitionConfig].toProvider[ApiDefinitionConfigProvider],
      bind[ApiScopeConfig].toProvider[ApiScopeConfigProvider],
      bind[ApiSSubscriptionFieldsConfig].toProvider[ApiSSubscriptionFieldsConfigProvider],
      bind[ServiceLocatorConfig].toProvider[ServiceLocatorConfigProvider]
    )
  }
}

object ConfigHelper {

  def getConfig[T](key: String, f: String => Option[T]): T = {
    f(key).getOrElse(throw new RuntimeException(s"[$key] is not configured!"))
  }
}

@Singleton
class DocumentationConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[DocumentationConfig] {

  override def get() = {
    val publishApiDefinition = runModeConfiguration.getBoolean("publishApiDefinition").getOrElse(false)
    val apiContext = runModeConfiguration.getString("api.context").getOrElse("api-publisher")
    val access = runModeConfiguration.getConfig(s"api.access")
    DocumentationConfig(publishApiDefinition, apiContext, access)
  }
}

@Singleton
class ApiDefinitionConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
  extends Provider[ApiDefinitionConfig] {

  override def get() = {
    val baseUrl = servicesConfig.baseUrl("api-definition")
    ApiDefinitionConfig(baseUrl)
  }
}

@Singleton
class ApiScopeConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
  extends Provider[ApiScopeConfig] {

  override def get() = {
    val baseUrl = servicesConfig.baseUrl("api-scope")
    ApiScopeConfig(baseUrl)
  }
}

@Singleton
class ApiSSubscriptionFieldsConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
  extends Provider[ApiSSubscriptionFieldsConfig] {

  override def get() = {
    val baseUrl = servicesConfig.baseUrl("api-subscription-fields")
    ApiSSubscriptionFieldsConfig(baseUrl)
  }
}

@Singleton
class ServiceLocatorConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
  extends Provider[ServiceLocatorConfig] {

  override def get() = {
    val baseUrl = servicesConfig.baseUrl("service-locator")
    ServiceLocatorConfig(baseUrl)
  }
}
