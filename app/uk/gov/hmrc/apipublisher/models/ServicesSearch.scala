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

package uk.gov.hmrc.apipublisher.models

final case class ServicesSearch(
    filters: List[ServicesSearchFilter] = List.empty,
    sort: ServicesSort = ServicesNoSorting
  )

object ServicesSearch {

  def fromQueryString(queryString: Map[String, Seq[String]]): ServicesSearch = {

    def filters = queryString.flatMap {
      case (key, values) =>
        key match {
          case "status" => ServicesStatusFilter(values)
          case _        => None // ignore anything that isn't a search filter
        }
    }
      .filter(searchFilter => searchFilter.isDefined)
      .flatten
      .toList

    new ServicesSearch(filters)
  }
}

sealed trait ServicesSearchFilter

sealed trait ServicesStatusFilter extends ServicesSearchFilter
case object New                   extends ServicesStatusFilter
case object Approved              extends ServicesStatusFilter
case object Failed                extends ServicesStatusFilter
case object Resubmitted           extends ServicesStatusFilter

case object ServicesStatusFilter {

  def apply(values: Seq[String]): Seq[Option[ServicesStatusFilter]] = {
    values.map {
      case "NEW"         => Some(New)
      case "APPROVED"    => Some(Approved)
      case "FAILED"      => Some(Failed)
      case "RESUBMITTED" => Some(Resubmitted)
      case _             => None
    }
  }
}

sealed trait ServicesSort
case object ServicesNoSorting extends ServicesSort
