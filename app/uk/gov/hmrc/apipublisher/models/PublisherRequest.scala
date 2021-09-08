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

package uk.gov.hmrc.apipublisher.models

import play.api.libs.json._
import uk.gov.hmrc.apipublisher.models.APICategory.{APICategory, formatAPICategory}
import uk.gov.hmrc.http.UnprocessableEntityException

case class ApiAndScopes(api: JsObject, scopes: Option[JsArray]) {

  def validateAPIScopesAreDefined(): Unit = {
    val missing: Seq[String] = apiScopes.filterNot(definedScopes.contains)
    if (missing.nonEmpty) {
      throw new UnprocessableEntityException(s"Undefined scopes used in definition: ${missing.mkString("[", ", ", "]")}")
    }
  }

  private lazy val definedScopes: Seq[String] = (scopes.getOrElse(JsArray()) \\ "key").map(_.as[String])

  private lazy val apiScopes: Seq[String] = (api \ "versions" \\ "scope").map(_.as[String])

  private lazy val versions: JsArray = (api \ "versions").as[JsArray]

  private lazy val versionsWithoutFieldDefinitions: JsArray = {
    val pruneFieldDefinitions = (__ \ 'fieldDefinitions).json.prune
    val newJsArrayValue = versions.value map { versionJs =>
      lazy val versionNo = (versionJs \ "version").asOpt[String].getOrElse("N/A")
      transformJson(versionJs, pruneFieldDefinitions, s"Could not prune field definitions from version $versionNo")
    }
    JsArray(newJsArrayValue)
  }

  lazy val apiWithoutFieldDefinitions: JsObject = {
    val prune = (__ \ 'versions).json.prune
    val putNew = __.json.update((__ \ 'versions).json.put(versionsWithoutFieldDefinitions))
    val replaceVersions = prune andThen putNew
    transformJson(api, replaceVersions, s"Could not put versions without field definitions in api")
  }

  lazy val apiName: String = {
    (api \ "name").as[String]
  }

  lazy val description : Option[String] = {
    (api \\ "description").headOption.map(_.as[String])
  }

  lazy val apiContext: String = {
    (api \ "context").as[String]
  }

  lazy val categories: Seq[APICategory] = {
    (api \ "categories").asOpt[Seq[APICategory]].getOrElse(Seq.empty)
  }

  lazy val versionNumbers: Seq[String] = versions.value.map(v => (v \ "version").as[String])

  lazy val fieldDefinitions: Seq[ApiFieldDefinitions] = {
    versions.value.flatMap(versionJs => readFieldDefinitionsForVersion(versionJs))
  }

  private def readFieldDefinitionsForVersion(versionJs: JsValue): Option[ApiFieldDefinitions] = {
    versionJs.validate[OptionalFieldDefinitions](OptionalFieldDefinitions.reads) match {
      case success: JsSuccess[OptionalFieldDefinitions] => for {
          fieldDefinitions <- success.get.fieldDefinitions
          apiVersion = success.get.version
        } yield ApiFieldDefinitions(apiContext, apiVersion, fieldDefinitions)

      case error: JsError =>
        throw new UnprocessableEntityException(s"Could not parse versions element for field definitions: ${JsError.toJson(error)}")
    }
  }

  private def transformJson(srcJson: JsValue, transformer: Reads[JsObject], errorClue: => String): JsObject = {
    srcJson.as[JsObject].transform(transformer) match {
      case JsSuccess(js, _) => js
      case error: JsError => throw new UnprocessableEntityException(s"$errorClue: ${JsError.toJson(error)}")
    }
  }

}

object ApiAndScopes {
  implicit val formats = Json.format[ApiAndScopes]
}

case class OptionalFieldDefinitions(version: String, fieldDefinitions: Option[Seq[FieldDefinition]])

object OptionalFieldDefinitions {
  implicit val reads = Json.reads[OptionalFieldDefinitions]
}

case class Scope(key: String, name: String, description: String)

object Scope {
  implicit val formats = Json.format[Scope]
}

case class ServiceLocation(serviceName: String, serviceUrl: String, metadata: Option[Map[String, String]] = None)

object ServiceLocation {
  implicit val formats = Json.format[ServiceLocation]
}
