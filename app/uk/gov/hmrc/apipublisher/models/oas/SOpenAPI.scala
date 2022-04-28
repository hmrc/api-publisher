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

package uk.gov.hmrc.apipublisher.models.oas

import scala.collection.immutable.ListMap
import java.util.function.BiConsumer
import io.swagger.v3.oas.models._
import uk.gov.hmrc.apipublisher.models.oas.SOpenAPI.Helpers._
import io.swagger.models.Method
import scala.collection.mutable.Buffer
import io.swagger.v3.oas.models.parameters.Parameter
import scala.collection.JavaConverters._

object SOpenAPI {
  object Helpers {
    // type SExtensions = Map[String, Object]

    implicit class FromNullableList[A](list: java.util.List[A]) {
      def fromNullableList: List[A] = Option(list).map(_.asScala.toList).getOrElse(List.empty)
    }

    // implicit class AsImmutableMapSyntax[A,B](wrap: java.util.Map[A,B]) {
    //   def asScalaIMap: Map[A,B] = Map(wrap.asScala.toSeq: _*)
    // }
    implicit class AsImmutableListMapMapSyntax[A,B](wrap: java.util.LinkedHashMap[A,B]) {
      def asScalaListMap: ListMap[A,B] = {
        val buffer = Buffer[(A, B)]()
        val bc = new BiConsumer[A, B] {
          def accept(k: A, v: B): Unit = { buffer.append( (k,v) ) }
        }
        wrap.forEach(bc)

        ListMap.apply(buffer: _*)
      }
    }
  }
}

final case class ParameterIn(value: String) {
  require(List("query", "header", "path", "cookie") contains value)
}

final case class ParameterName(value: String) extends AnyVal

final case class ParameterKey(name: ParameterName, in: ParameterIn)


final case class SOpenAPI(inner: OpenAPI) {
  lazy val paths: SPaths = SPaths(inner.getPaths)
}

case class SPaths(inner: Paths) {
  lazy val pathItems: ListMap[String, SPathItem] = inner.asScalaListMap.map { case (k,v) => k -> (SPathItem(v)) }
}

case class SPathItem(inner: PathItem) {
  lazy val summary: Option[String] = Option(inner.getSummary)

  private def add(method: Method, op: Operation) = 
    Option(op).fold(ListMap.empty[Method,SOperation])(v => ListMap(method -> SOperation(v)))

  lazy val ops: ListMap[String, SOperation] = 
    (
      add(Method.DELETE, inner.getDelete) ++
      add(Method.GET, inner.getGet) ++
      add(Method.HEAD, inner.getHead) ++
      add(Method.OPTIONS, inner.getOptions) ++
      add(Method.PATCH, inner.getPatch) ++
      add(Method.POST, inner.getPost) ++
      add(Method.PUT, inner.getPut)
    )
    .map { case (k,v) => k.toValue.toUpperCase -> v }
}

case class SOperation(inner: Operation) {
  lazy val summary: Option[String] = Option(inner.getSummary)
  lazy val parameters: ListMap[ParameterKey, SParameter] = SParameters(inner.getParameters.fromNullableList)
  lazy val queryParameters: ListMap[ParameterName, SParameter] = 
    parameters.filter {
      case (ParameterKey(_, ParameterIn("query")), _) => true
      case _ => false
    }
    .map {
      case (ParameterKey(name, ParameterIn("query")), p: SParameter) => (name -> p)
    }
}

case class SParameter(
  inner: Parameter
) {
  lazy val required = inner.getRequired
}

object SParameters {
  def apply(in: List[Parameter]): ListMap[ParameterKey, SParameter] = {
    ListMap(
      in.map { p =>
        val key = ParameterKey(ParameterName(p.getName), ParameterIn(p.getIn()))
        val param = SParameter(p)

        (key, param)
      }
      : _*
    )
  }
}
