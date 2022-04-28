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

import javax.inject.Singleton
import io.swagger.v3.oas.models.OpenAPI
import uk.gov.hmrc.ramltools.domain.Endpoint
import uk.gov.hmrc.apipublisher.models.oas._
import uk.gov.hmrc.ramltools.domain.QueryParam

@Singleton
class OasParserImpl() extends OasVersionDefinitionService.OasParser {
     
  def apply(openAPI: OpenAPI): List[Endpoint] = {
    val sopenAPI = SOpenAPI(openAPI)

    val securitySchemes: Map[String, SSecurityScheme] = sopenAPI.components
      .map(_.securitySchemes)
      .getOrElse(Map.empty)

    sopenAPI.paths.pathItems.flatMap {
      case (urlPattern, sPathItem) =>
        sPathItem.ops.map {
          case (method, operation) =>
            val endpointName : String = operation.summary.getOrElse("no endpoint name provided")
            
            val queryParameters : Option[List[QueryParam]] = 
              operation.queryParameters.map {
                case (name, param) => QueryParam(name.value, param.required)
              }
              .toList match {
                case Nil => None
                case list => Some(list)
              }

            val (scheme, scope) = 
                (
                  for {
                    (schemeName, scope) <- operation.schemeAndScope
                    scheme              <- securitySchemes.get(schemeName)
                  }
                  yield (Some(scheme), scope)
                ).getOrElse( (None, None) )

            val authType = 
              (
                for {
                  scheme <- scheme
                  auth <- scheme match {
                    case _: OAuth2AuthorizationCodeSecurityScheme => Some("USER")
                    case _: OAuth2ClientCredentialsSecurityScheme => Some("APPLICATION")
                    case _ => Some("NONE")
                  }
                }
                yield auth
              ).getOrElse("NONE")

            Endpoint(
              urlPattern, 
              endpointName, 
              method, 
              authType,
              throttlingTier = "UNLIMITED", 
              scope,
              queryParameters
            )
        }.toList
    }
    .toList
  }
}
