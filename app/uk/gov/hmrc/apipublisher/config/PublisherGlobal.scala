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

import com.typesafe.config.Config
import play.api._
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config._
import uk.gov.hmrc.play.http.ws.{WSDelete, WSGet, WSPost, WSPut}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.apipublisher.services.RegistrationService

import scala.concurrent.ExecutionContext.Implicits.global

object PublisherGlobal extends DefaultMicroserviceGlobal with RunMode {

  override def loggingFilter: LoggingFilter = MicroserviceLoggingFilter

  override def microserviceAuditFilter: AuditFilter = MicroserviceAuditFilter

  override def authFilter = None

  override def auditConnector: AuditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] =
    app.configuration.getConfig(s"$env.microservice.metrics")

  object ControllerConfiguration extends ControllerConfig {

    import net.ceedubs.ficus.Ficus._

    lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
  }

  object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
    override val auditConnector = MicroserviceAuditConnector

    override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
  }

  object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
    override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
  }

  object MicroserviceAuditConnector extends AuditConnector with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
  }

  override def onStart(app: play.api.Application) = {
    super.onStart(app)
    Logger.info(s"Starting api-publisher in mode : ${app.mode}")
    val registrationService = app.injector.instanceOf[RegistrationService]
    registrationService.registerPublishCallback().map {
      _ => Logger.info(s"Publisher has subscribed to the service locator")
    }.recover {
      case e => Logger.error(s"Publisher could not subscribe to the service locator", e)
    }
  }
}

object MicroserviceAuditConnector extends AuditConnector with RunMode {
  override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
}

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector: AuditConnector = MicroserviceAuditConnector
}

trait WSHttp extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete with Hooks with AppName
object AuditedWSHttp extends WSHttp
