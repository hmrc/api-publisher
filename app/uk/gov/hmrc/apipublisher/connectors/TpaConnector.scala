/*
 * Copyright 2024 HM Revenue & Customs
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
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.Status.UNPROCESSABLE_ENTITY
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

object TpaConnector {

  case class Config(
      serviceBaseUrl: String
    )
}

@Singleton
class TpaConnector @Inject() (config: TpaConnector.Config, http: HttpClientV2)(implicit val ec: ExecutionContext) {

  protected val serviceBaseUrl: String = config.serviceBaseUrl

  def deleteSubscriptions(apiContext: String, versionNbr: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"$serviceBaseUrl/apis/$apiContext/versions/$versionNbr/subscribers"
    http.delete(url"$url")
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .map {
        case Right(_)                                                         => (())
        case Left(UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _)) => throw new UnprocessableEntityException(message)
        case Left(err)                                                        => throw err
      }
  }
}
