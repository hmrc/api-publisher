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

package uk.gov.hmrc.apipublisher.services

import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

abstract class AbstractDefinitionService(microserviceConnector: MicroserviceConnector)(implicit val ec: ExecutionContext) {
  
  def getDefinition(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Option[ApiAndScopes]] = {
    microserviceConnector.getAPIAndScopes(serviceLocation).flatMap {
      case Some(apiAndScopes) => addDetailFromSpecification(serviceLocation, apiAndScopes) match {
        case Success(data) => successful(Some(data))
        case Failure(ex) => failed(ex)
      }
      case None => successful(None)
    }
  }

  protected def addDetailFromSpecification(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes): Try[ApiAndScopes]

}
