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

package uk.gov.hmrc.apipublisher.services

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.domain.Endpoints

import scala.concurrent.Future
import uk.gov.hmrc.ramltools.domain.Endpoint
import uk.gov.hmrc.apipublisher.models.ServiceLocation
import scala.util.Try

@Singleton
class RamlVersionDefinitionService @Inject() (microserviceConnector: MicroserviceConnector) extends DefinitionService.VersionDefinitionService {

  override def getDetailForVersion(serviceLocation: ServiceLocation, context: Option[String], version: String): Future[List[Endpoint]] = {
    Future.fromTry(
      getRamlForVersion(serviceLocation, version).map { raml =>
        Endpoints(raml, context).toList
      }
    )
  }

  private def getRamlForVersion(serviceLocation: ServiceLocation, version: String): Try[RAML] = {
    microserviceConnector.getRaml(serviceLocation, version)
  }
}
