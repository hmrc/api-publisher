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

import scala.concurrent.{ExecutionContext, Future}
import cats.data.OptionT
import cats.implicits._

abstract class AbstractDefinitionService(microserviceConnector: MicroserviceConnector)(implicit val ec: ExecutionContext) {
  
  def getDefinition(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Option[ApiAndScopes]] = {
    (
      for {
        baseApiAndScopes     <- OptionT(microserviceConnector.getAPIAndScopes(serviceLocation))
        detailedApiAndScopes <- OptionT(addDetailFromSpecification(serviceLocation, baseApiAndScopes))
      } 
      yield detailedApiAndScopes
    )
    .value
  }

  protected def addDetailFromSpecification(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes): Future[Option[ApiAndScopes]]

}
