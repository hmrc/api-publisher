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

package uk.gov.hmrc.apipublisher.connectors

import javax.inject.{Inject, Singleton}
import play.api.http.Status.NO_CONTENT
import uk.gov.hmrc.apipublisher.models.{ApiAndScopes, ServiceLocation}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, OptionHttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.loaders.RamlLoader

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class MicroserviceConnector @Inject()(ramlLoader: RamlLoader, http: HttpClient)
                                     (implicit val ec: ExecutionContext) extends ConnectorRecovery with OptionHttpReads {

  // Overridden so we can map only 204 to None, rather than also including 404
  implicit override def readOptionOf[P](implicit rds: HttpReads[P]): HttpReads[Option[P]] = new HttpReads[Option[P]] {
    def read(method: String, url: String, response: HttpResponse): Option[P] = response.status match {
      case NO_CONTENT => None
      case _ => Some(rds.read(method, url, response))
    }
  }

  def getAPIAndScopes(serviceLocation: ServiceLocation)(implicit hc: HeaderCarrier): Future[Option[ApiAndScopes]] = {
    val url = s"${serviceLocation.serviceUrl}/api/definition"
    http.GET[Option[ApiAndScopes]](url) recover unprocessableRecovery
  }

  def getRaml(serviceLocation: ServiceLocation, version: String): Try[RAML] = {
    ramlLoader.load(s"${serviceLocation.serviceUrl}/api/conf/$version/application.raml")
  }
}
