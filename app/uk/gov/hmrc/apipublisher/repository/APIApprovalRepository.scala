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
import org.bson.BsonValue
import org.mongodb.scala.bson.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters.{equal, exists, in, or}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}

import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apipublisher.models.APIApproval._
import uk.gov.hmrc.apipublisher.models.ApprovalStatus._
import uk.gov.hmrc.apipublisher.models._

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
  override lazy val requiresTtlIndex: Boolean = false

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

  def searchServices(searchCriteria: ServicesSearch): Future[Seq[APIApproval]] = {
    val statusFilters = convertFilterToStatusQueryClause(searchCriteria.filters)
    runQuery(statusFilters)
  }

  private def convertFilterToStatusQueryClause(filters: List[ServicesSearchFilter]): Bson = {

    def statusMatch(states: ApprovalStatus*): Bson = {
      if (states.isEmpty) {
        Document()
      } else {
        val bsonStates = states.map(s => Codecs.toBson(s))
        in("status", bsonStates: _*)
      }
    }

    def getFilterState(filter: ServicesSearchFilter): ApprovalStatus = {
      filter match {
        case New         => NEW
        case Approved    => APPROVED
        case Failed      => FAILED
        case Resubmitted => RESUBMITTED
      }
    }

    val statusFilters = filters.collect { case sf: ServicesSearchFilter => sf }
    statusMatch(statusFilters.map(sf => getFilterState(sf)): _*)
  }

  private def runQuery(statusFilters: Bson) = {
    collection.aggregate[BsonValue](
      Seq(
        filter(statusFilters)
      )
    ).map(Codecs.fromBson[APIApproval])
      .toFuture()
  }
}
