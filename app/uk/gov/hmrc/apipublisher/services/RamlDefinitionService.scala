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
import uk.gov.hmrc.ramltools.RAML
import uk.gov.hmrc.ramltools.domain.Endpoints

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future

@Singleton
class RamlDefinitionService @Inject()(microserviceConnector: MicroserviceConnector)(implicit ec: ExecutionContext) extends AbstractDefinitionService(microserviceConnector)(ec) {

  override protected def addDetailFromSpecification(serviceLocation: ServiceLocation, apiAndScopes: ApiAndScopes): Future[Option[ApiAndScopes]] = {
    Future.fromTry {
      val api = apiAndScopes.api
      val context = (api \ "context").asOpt[String]

      val versions: List[Try[JsObject]] =
        (api \ "versions").as[List[JsObject]].map { v =>
          getRamlForVersion(serviceLocation, (v \ "version").as[String]).map { raml =>
            populateVersionFromRaml(v, raml, context)
          }
        }

      flipSequenceOfTrys(versions).map(vers =>
        apiAndScopes.copy(api = api + ("versions" -> JsArray(vers)))
      )
    }
    .map(Some(_))
  }

  private def populateVersionFromRaml(version: JsObject, raml: RAML, context: Option[String]): JsObject = {
    version + ("endpoints" -> Json.toJson(Endpoints(raml, context)).as[JsArray])
  }

  private def getRamlForVersion(serviceLocation: ServiceLocation, version: String): Try[RAML] = {
    microserviceConnector.getRaml(serviceLocation, version)
  }

  private def flipSequenceOfTrys(versions: List[Try[JsObject]]): Try[Seq[JsObject]] = {
    @scala.annotation.tailrec
    def loop(trys: List[Try[JsObject]], acc: Seq[JsObject]): Try[Seq[JsObject]] = {
      trys match {
        case Nil => Success(acc)
        case Success(obj) :: t => loop(t, acc :+ obj)
        case Failure(ex) :: _ => Failure(ex)
      }
    }

    loop(versions, Seq.empty)
  }
}
