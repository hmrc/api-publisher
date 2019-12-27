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
import uk.gov.hmrc.play.config.ServicesConfig

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[ApiDefinitionConfig].toProvider[ApiDefinitionConfigProvider],
      bind[ApiScopeConfig].toProvider[ApiScopeConfigProvider],
      bind[ApiSSubscriptionFieldsConfig].toProvider[ApiSSubscriptionFieldsConfigProvider],
      bind[MicroserviceConfig].toProvider[MicroserviceConfigProvider]
    )
  }
}

@Singleton
class ApiDefinitionConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiDefinitionConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get(): ApiDefinitionConfig = {
    val serviceBaseUrl = baseUrl("api-definition")
    ApiDefinitionConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiScopeConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiScopeConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get(): ApiScopeConfig = {
    val serviceBaseUrl = baseUrl("api-scope")
    ApiScopeConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiSSubscriptionFieldsConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[ApiSSubscriptionFieldsConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get(): ApiSSubscriptionFieldsConfig = {
    val serviceBaseUrl = baseUrl("api-subscription-fields")
    ApiSSubscriptionFieldsConfig(serviceBaseUrl)
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
