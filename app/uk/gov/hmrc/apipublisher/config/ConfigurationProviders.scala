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

package uk.gov.hmrc.apipublisher.config

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.FiniteDuration

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import uk.gov.hmrc.apipublisher.connectors._

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[ApiDefinitionConfig].toProvider[ApiDefinitionConfigProvider],
      bind[ApiScopeConfig].toProvider[ApiScopeConfigProvider],
      bind[ApiSSubscriptionFieldsConfig].toProvider[ApiSSubscriptionFieldsConfigProvider],
      bind[MicroserviceConnector.Config].toProvider[MicroserviceConnectorConfigProvider]
    )
  }
}

@Singleton
class ApiDefinitionConfigProvider @Inject() (val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
    extends Provider[ApiDefinitionConfig] {

  override def get(): ApiDefinitionConfig = {
    val serviceBaseUrl = servicesConfig.baseUrl("api-definition")
    ApiDefinitionConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiScopeConfigProvider @Inject() (val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
    extends Provider[ApiScopeConfig] {

  override def get(): ApiScopeConfig = {
    val serviceBaseUrl = servicesConfig.baseUrl("api-scope")
    ApiScopeConfig(serviceBaseUrl)
  }
}

@Singleton
class ApiSSubscriptionFieldsConfigProvider @Inject() (val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
    extends Provider[ApiSSubscriptionFieldsConfig] {

  override def get(): ApiSSubscriptionFieldsConfig = {
    val serviceBaseUrl = servicesConfig.baseUrl("api-subscription-fields")
    ApiSSubscriptionFieldsConfig(serviceBaseUrl)
  }
}

@Singleton
class MicroserviceConnectorConfigProvider @Inject() (val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig)
    extends Provider[MicroserviceConnector.Config] {

  override def get(): MicroserviceConnector.Config = {
    val validateApiDefinition = runModeConfiguration.getOptional[Boolean]("validateApiDefinition").getOrElse(true)
    val oasParserMaxDuration  = FiniteDuration(runModeConfiguration.getMillis("oasParserMaxDuration"), TimeUnit.MILLISECONDS)

    MicroserviceConnector.Config(validateApiDefinition, oasParserMaxDuration)
  }
}
