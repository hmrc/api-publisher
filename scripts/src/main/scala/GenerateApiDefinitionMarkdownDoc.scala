/*
 * Copyright 2025 HM Revenue & Customs
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

import scala.annotation.tailrec
import scala.collection.mutable
import scala.io.Source
import scala.util.{Failure, Success, Using}

import io.circe._
import io.circe.parser._
import java.io.PrintWriter

object GenerateApiDefinitionMarkdownDoc {

  case class Config(schemaFile: String = "", markdownFile: String = "")

  def main(args: Array[String]): Unit = {
    parseArgs(args.toList) match {
      case Some(config) => processSchema(config)
      case None =>
        printUsage()
        sys.exit(1)
    }
  }

  private def parseArgs(args: List[String], config: Config = Config()): Option[Config] = {
    args match {
      case Nil => None
      case ("-h" | "--help") :: _ => None
      case _ :: ("-h" | "--help") :: _ => None
      case argOne :: argTwo :: Nil if !argOne.startsWith("-") && !argTwo.startsWith("-") =>
        Some(Config(argOne, argTwo))
      case unknownOption :: _ =>
        System.err.println(s"Unknown option: $unknownOption")
        None
    }
  }

  private def printUsage(): Unit = {
    println("Generate documentation from a JSON schema")
    println("Usage: sbt generateDoc [OPTIONS] INPUT-FILE OUTPUT-FILE")
    println("  INPUT-FILE             JSON file containing the JSON schema")
    println("  OUTPUT-FILE            Markdown file containing the processed schema")
    println("  -h, --help             Show this help message")
  }

  private def processSchema(config: Config): Unit = {
    Using(Source.fromFile(config.schemaFile))(_.mkString) match {
      case Failure(exception) =>
        System.err.println(s"Error reading file: ${exception.getMessage}")
        sys.exit(1)
      case Success(jsonString) =>
        parse(jsonString) match {
          case Left(err) =>
            System.err.println(s"Failed to parse JSON: $err")
            sys.exit(1)
          case Right(json) =>
            val schema = json.hcursor
            val text = s"# ${schema.getString("description")}" :: s"Generated from [JSON schema](../${config.schemaFile})" :: outputObject("root", schema)
            Using(new PrintWriter(config.markdownFile))(file => text.map(file.println))
        }
    }
  }

  /**
   * Output the markdown for a JSON schema object
   *
   * @param objectName : Name of the JSON schema object
   * @param schema     : A Circe cursor for the object
   * @param level      : The depth of the JSON scheme object in the tree
   */
  private def outputObject(objectName: String, schema: ACursor, level: Int = 2): List[String] = {
    val requiredFields = schema.get[List[String]]("required").getOrElse(List.empty)

    val output = mutable.ArrayBuffer[String]()

    output.append(s"${"#" * level} `$objectName`")
    output.append(schema.getString("description"))
    output.append("")

    val properties = schema.downField("properties")
    val propertyKeys = properties.keys.getOrElse(List.empty)

    if (propertyKeys.nonEmpty) {
      output.append("| Name | Type | Required | Values | Description |")
      output.append("| --- | --- | --- | --- | --- |")

      val nestedObjects = propertyKeys.foldLeft(List.empty[(String, ACursor)]) { (nested, propertyKey) =>
        val property = properties.downField(propertyKey).success.get
        val fieldType = property.getString("type")
        val required = if (requiredFields.contains(propertyKey)) "Required" else "Optional"

        output.append(outputRow(propertyKey, property, required))

        fieldType match {
          case "object" =>
            nested :+ (propertyKey -> property)
          case "array" =>
            val items = property.downField("items")
            if (items.getString("type") == "object") {
              nested :+ (propertyKey -> items.success.get)
            } else {
              nested
            }
          case _ =>
            nested
        }
      }

      nestedObjects.foreach { case (nestedName, nestedSchema) =>
        output.appendAll(outputObject(nestedName, nestedSchema, level = 3))
      }
    }
    
    output.toList
  }

  private val anchorCounts = mutable.Map.empty[String, Int]

  /**
   * Output a table row detailing information about a JSON schema property
   *
   * @param propertyKey : Key of a JSON schema property
   * @param property    : A Circe cursor for the property
   * @param required    : True if the key Required, false if it is Optional
  */
  private def outputRow(propertyKey: String, property: ACursor, required: String): String = {
    val items = property.downField("items")

    // If the data type is an array, get details of the type within the array
    val fieldType = property.getString("type") match {
      case "array" => s"${items.getString("type")}[]"
      case other   => other
    }

    val enumValues = fieldType match {
      case "string[]" => items.getList("enum")
      case _ => property.getList("enum")
    }

    // If the data type is an object, make it into a link
    val objectLink = fieldType match {
      case "object" | "object[]" =>
        val count = anchorCounts.getOrElse(propertyKey, 0)
        anchorCounts(propertyKey) = count + 1
        val anchor = s"$propertyKey${if (count == 0) "" else s"-$count"}"
        Some(s"[${propertyKey.toLowerCase}](#$anchor)")
      case _                     => None
    }

    // If the data type is a string with a pattern, use the regex pattern as the value
    val patternValue = fieldType match {
      case "string" => property.get[String]("pattern").toOption
      case _        => None
    }

    // The default value might be a Boolean, so can't get a String here
    val defaultValue = property.get[Json]("default").toOption
      .map(_.toString.capitalize)
      .map(d => s"$d (default)")

    // Separate enum lists with a <br>
    val values = enumValues
      .map(_.mkString("<br>"))
      .orElse(defaultValue)
      .orElse(patternValue)
      .orElse(objectLink)
      .getOrElse("")

    s"| `$propertyKey` | _${fieldType}_ | $required | $values | ${property.getString("description")} |"
  }

  implicit class ACursorSyntax(cursor: ACursor) {
    def getString(field: String): String = cursor.get[String](field).getOrElse("")
    def getList(field: String): Option[List[String]] = cursor.get[List[String]](field).toOption
  }
}
