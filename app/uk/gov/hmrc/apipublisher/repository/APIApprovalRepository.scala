/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.apipublisher.repository

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.apipublisher.models.APIApproval
import uk.gov.hmrc.apipublisher.models.APIApproval._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import scala.concurrent.{ExecutionContext, Future}
import scala.Option.empty


@Singleton
class APIApprovalRepository @Inject()(mongo: ReactiveMongoComponent)(implicit val ec: ExecutionContext)
  extends ReactiveRepository[APIApproval, BSONObjectID]("apiapproval", mongo.mongoConnector.db,
    apiApprovalFormat, ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    Index(Seq("serviceName" -> IndexType.Ascending), name = Some("serviceNameIndex"), unique = true, background = true))

  indexes.map(collection.indexesManager.ensure)

  def save(apiApproval: APIApproval): Future[APIApproval] = {
    collection.find[JsObject, JsObject](selector = Json.obj("serviceName" -> apiApproval.serviceName)).one[BSONDocument].flatMap {
      case Some(document) => collection.update(ordered=false).one(q = BSONDocument("_id" -> document.get("_id")), u = apiApproval)
      case None => collection.insert(ordered=false).one(apiApproval)
    }.map(_ => apiApproval)
  }

  def fetch(serviceName: String): Future[Option[APIApproval]] = {
    Logger.info(s"Fetching API $serviceName in mongo")
    collection.find[JsObject, JsObject](selector = Json.obj("serviceName" -> serviceName)).one[APIApproval].map { apiApproval =>
      Logger.debug(s"Retrieved apiApproval $serviceName in mongo: $apiApproval")
      apiApproval
    }
  }

  def or(op1: JsObject, op2: JsObject) : JsObject = Json.obj("$or" -> Json.arr(op1, op2))

  def exists(field: String) : JsObject = Json.obj(field -> Json.obj("$exists" -> true))

  def notExists(field: String): JsObject = Json.obj(field -> Json.obj("$exists" -> false))

  def equals(field: String, value: Boolean): JsObject = Json.obj(field -> Json.obj("$eq" -> value))

  def equals[T <: JsValueWrapper](field: String, value: T): JsObject = Json.obj(field -> Json.obj("$eq" -> value))

  def fetchUnapprovedServices(): Future[Seq[APIApproval]] = {
    val query = or(equals("approved", value = false), notExists("approved"))
    collection.find[JsObject, JsObject](query, empty)
      .cursor[APIApproval]()
      .collect[Seq](-1, FailOnError[Seq[APIApproval]]())
  }
}
