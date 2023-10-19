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

package uk.gov.hmrc.apipublisher.models.oas

import java.util.function.BiConsumer
import scala.collection.immutable.ListMap
import scala.collection.mutable.Buffer
import scala.jdk.CollectionConverters._

import io.swagger.models.Method
import io.swagger.v3.oas.models._
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}

import uk.gov.hmrc.apipublisher.models.oas.SOpenAPI.Helpers._

object SOpenAPI {

  object Helpers {

    implicit class FromNullableList[A](list: java.util.List[A]) {
      def asOptionOfList: Option[List[A]] = Option(list).map(_.asScala.toList)
      def fromNullableList: List[A]       = asOptionOfList.getOrElse(List.empty)
    }

    implicit class AsImmutableMapSyntax[A, B](wrap: java.util.Map[A, B]) {
      def asScalaIMap: Map[A, B] = Map(Option(wrap).map(_.asScala).getOrElse(Map.empty).toSeq: _*)
    }

    implicit class AsImmutableListMapMapSyntax[A, B](wrap: java.util.LinkedHashMap[A, B]) {

      def asScalaListMap: ListMap[A, B] = {
        val buffer = Buffer[(A, B)]()
        val bc     = new BiConsumer[A, B] {
          def accept(k: A, v: B): Unit = { buffer.append((k, v)) }
        }
        wrap.forEach(bc)

        ListMap.apply(buffer.toSeq: _*)
      }
    }
  }
}

final case class ParameterIn(value: String) {
  require(List("query", "header", "path", "cookie") contains value, s"Value (${Option(value).getOrElse("NULL")}) is not a valid parameter type")
}

final case class ParameterName(value: String) extends AnyVal

final case class ParameterKey(name: ParameterName, in: ParameterIn)

final case class SOpenAPI(inner: OpenAPI) {
  lazy val components: Option[SComponents] = Option(inner.getComponents()).map(SComponents)

  lazy val securitySchemes: Map[String, SSecurityScheme] =
    components
      .map(_.securitySchemes)
      .getOrElse(Map.empty)

  lazy val defaultSecurityRequirements = inner.getSecurity().fromNullableList

  lazy val paths: SPaths = SPaths(inner.getPaths, securitySchemes, defaultSecurityRequirements)
}

case class SPaths(pathItems: ListMap[String, SPathItem])

object SPaths {

  def apply(inner: Paths, securitySchemes: Map[String, SSecurityScheme], defaultSecurityRequirements: List[SecurityRequirement]): SPaths = {
    SPaths(inner.asScalaListMap.map { case (k, v) => k -> (SPathItem(v, securitySchemes, defaultSecurityRequirements)) })
  }
}

case class SPathItem(summary: Option[String], ops: ListMap[String, SOperation])

object SPathItem {

  def apply(inner: PathItem, securitySchemes: Map[String, SSecurityScheme], defaultSecurityRequirements: List[SecurityRequirement]): SPathItem = {
    val summary: Option[String] = Option(inner.getSummary)

    def add(method: Method, op: Operation) =
      Option(op).fold(ListMap.empty[Method, SOperation])(v => ListMap(method -> SOperation(v, securitySchemes, defaultSecurityRequirements)))

    val ops: ListMap[String, SOperation] =
      (
        add(Method.DELETE, inner.getDelete) ++
          add(Method.GET, inner.getGet) ++
          add(Method.HEAD, inner.getHead) ++
          add(Method.OPTIONS, inner.getOptions) ++
          add(Method.PATCH, inner.getPatch) ++
          add(Method.POST, inner.getPost) ++
          add(Method.PUT, inner.getPut)
      )
        .map { case (k, v) => k.toValue.toUpperCase -> v }

    SPathItem(summary, ops)
  }
}

sealed trait SSecurityRequirement

case object OpenSSecurityRequirement                                                                     extends SSecurityRequirement
case class OauthSSecurityRequirement(schemeName: String, scheme: SSecurityScheme, scope: Option[String]) extends SSecurityRequirement

object SSecurityRequirement {

  def apply(
      listOfSecurityRequirements: java.util.List[SecurityRequirement],
      securitySchemes: Map[String, SSecurityScheme],
      defaultSecurityRequirements: List[SecurityRequirement]
    ): SSecurityRequirement = {
    // Multiple security schemes allowed by OAS 3, for which there is a map of key values of config
    // We only allow ONE scheme at present in Api Platform
    // We only allow OAuth2 so this is a scheme name and a list of scopes
    def changeSecurityRequirementToSchemesAndScopes(in: SecurityRequirement): List[(String, List[String])] =
      in.asScalaListMap.toList.map {
        case (name, scopes) => (name -> scopes.asScala.toList)
      }

    def lookupSchemeName(schemeName: String): SSecurityScheme = {
      securitySchemes.get(schemeName) match {
        case None         => throw new IllegalStateException(s"Failed to find scheme of name $schemeName in component.securitySchemes")
        case Some(scheme) => scheme
      }
    }

    def validateScope(scheme: SSecurityScheme, schemeName: String, scope: String): Unit = {
      if (!scheme.scopes.contains(scope)) {
        throw new IllegalStateException(s"Failed to find scope $scope in scheme: ${schemeName}")
      }
    }

    val listOrDefault =
      listOfSecurityRequirements.fromNullableList match {
        case Nil          => defaultSecurityRequirements
        case head :: tail => head :: tail
      }

    val securityRequirement = listOrDefault match {
      case Nil                                               => throw new IllegalStateException("Cannot have an endpoint with no explicit security")
      case (securityRequirement: SecurityRequirement) :: Nil => {
        changeSecurityRequirementToSchemesAndScopes(securityRequirement) match {
          case Nil                         => OpenSSecurityRequirement
          case (schemeName, scopes) :: Nil => {
            val scheme = lookupSchemeName(schemeName)
            scopes match {
              case Nil            => OauthSSecurityRequirement(schemeName, scheme, None)
              case (scope :: Nil) => validateScope(scheme, schemeName, scope); OauthSSecurityRequirement(schemeName, scheme, Some(scope))
              case _              => throw new IllegalStateException(s"API Platform only supports one scope per security requirement for scheme: $schemeName")
            }
          }
          case _                           => throw new IllegalStateException("API Platform only supports one scheme per endpoint")
        }
      }
      case _                                                 => throw new IllegalStateException("API Platform only supports one security requirement per endpoint")
    }
    securityRequirement
  }
}

case class SOperation(
    summary: Option[String],
    parameters: ListMap[ParameterKey, SParameter],
    queryParameters: ListMap[ParameterName, SParameter],
    securityRequirement: SSecurityRequirement
  )

object SOperation {

  def apply(inner: Operation, securitySchemes: Map[String, SSecurityScheme], defaultSecurityRequirements: List[SecurityRequirement]): SOperation = {
    val summary: Option[String]                             = Option(inner.getSummary)
    val parameters: ListMap[ParameterKey, SParameter]       = SParameters(inner.getParameters.fromNullableList)
    val queryParameters: ListMap[ParameterName, SParameter] =
      parameters.filter {
        case (ParameterKey(_, ParameterIn("query")), _) => true
        case _                                          => false
      }
        .map {
          case (ParameterKey(name, ParameterIn("query")), p: SParameter) => (name -> p)
        }

    val securityRequirement = SSecurityRequirement(inner.getSecurity(), securitySchemes, defaultSecurityRequirements)

    SOperation(summary, parameters, queryParameters, securityRequirement)
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
        val key   = ParameterKey(ParameterName(p.getName), ParameterIn(p.getIn()))
        val param = SParameter(p)

        (key, param)
      }: _*
    )
  }
}

case class SComponents(
    inner: Components
  ) {

  lazy val securitySchemes = inner.getSecuritySchemes().asScalaIMap.map {
    case (name, scheme) =>
      (name -> SSecurityScheme(scheme))
  }
}

sealed trait SSecurityScheme {
  def scopes: Set[String]
}
case class OAuth2AuthorizationCodeSecurityScheme(scopes: Set[String]) extends SSecurityScheme
case class OAuth2ClientCredentialsSecurityScheme(scopes: Set[String]) extends SSecurityScheme

object OAuth2SecurityScheme {

  def apply(in: SecurityScheme): SSecurityScheme = {
    val maybeAuthCodeScheme    = Option(in.getFlows.getAuthorizationCode)
    val maybeClientCredsScheme = Option(in.getFlows.getClientCredentials)

    (maybeAuthCodeScheme, maybeClientCredsScheme) match {
      case (Some(a), _) => OAuth2AuthorizationCodeSecurityScheme(a.getScopes.asScalaIMap.keySet)
      case (_, Some(c)) => OAuth2ClientCredentialsSecurityScheme(c.getScopes.asScalaIMap.keySet)
      case _            => throw new IllegalStateException("Only supports up to one of authorization code or client credentials oauth flows")
    }
  }
}

object SSecurityScheme {

  def apply(in: SecurityScheme): SSecurityScheme = {
    in.getType.toString match {
      case "oauth2" => OAuth2SecurityScheme(in)
      case s        => throw new IllegalStateException(s"Publishing does not support security schemes other than oauth2 [$s]")
    }
  }
}
