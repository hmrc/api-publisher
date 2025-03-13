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

package uk.gov.hmrc.apipublisher.repository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import com.mongodb.client.model.ReplaceOptions
import org.mongodb.scala.model.Filters.{equal, exists, or}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}

import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apipublisher.models.APIApproval
import uk.gov.hmrc.apipublisher.models.APIApproval._

@Singleton
class APIApprovalRepository @Inject() (mongo: MongoComponent)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[APIApproval](
      collectionName = "apiapproval",
      mongoComponent = mongo,
      domainFormat = apiApprovalFormat,
      indexes = Seq(
        IndexModel(
          ascending("serviceName"),
          IndexOptions()
            .name("serviceNameIndex")
            .unique(true)
            .background(true)
        )
      ),
      replaceIndexes = true
    ) {

  def save(apiApproval: APIApproval): Future[APIApproval] = {
    val query = equal("serviceName", Codecs.toBson(apiApproval.serviceName))

    collection.replaceOne(query, apiApproval, new ReplaceOptions().upsert(true)).toFuture().map(_ => apiApproval)
  }

  def fetch(serviceName: String): Future[Option[APIApproval]] = {
    collection.find(equal("serviceName", Codecs.toBson(serviceName)))
      .headOption()
  }

  def fetchUnapprovedServices(): Future[Seq[APIApproval]] = {
    collection.find(or(
      equal("approved", Codecs.toBson(false)),
      exists("approved", false)
    )).toFuture()
      .map(_.toList)
  }

  def fetchAllServices(): Future[Seq[APIApproval]] = {
    collection.find.toFuture().map(_.toList)
  }

}
