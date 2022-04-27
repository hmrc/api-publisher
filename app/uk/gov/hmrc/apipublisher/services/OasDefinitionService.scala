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

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.apipublisher.connectors.MicroserviceConnector
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class OasDefinitionService @Inject()(microserviceConnector: MicroserviceConnector)(implicit ec: ExecutionContext) extends AbstractDefinitionService(microserviceConnector)(ec) {

  protected def addDetailFromSpecification(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes): Future[Option[ApiAndScopes]] = 
    successful(Some(ApiAndScopes(JsObject(Seq.empty), JsArray())))
}
