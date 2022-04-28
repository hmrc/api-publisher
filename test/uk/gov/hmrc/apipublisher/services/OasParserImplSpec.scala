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

import utils.HmrcSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import uk.gov.hmrc.apipublisher.util.ApplicationLogger
import scala.collection.JavaConverters._
import uk.gov.hmrc.ramltools.domain.QueryParam

class OasParserImplSpec extends HmrcSpec with ApplicationLogger {

  trait Setup {
    val parser = new OasParserImpl()

    def generate(oasSpec: String): OpenAPI = {
      val parseOptions = new ParseOptions();
      parseOptions.setResolve(true);
      val emptyAuthList = java.util.Collections.emptyList[io.swagger.v3.parser.core.models.AuthorizationValue]()

      val result = new OpenAPIV3Parser().readContents(oasSpec,emptyAuthList, parseOptions)
      if(result.getMessages.size > 0) {
        logger.warn("Failures: "+result.getMessages().asScala.mkString)
      }
      assert(result.getMessages().isEmpty())

      result.getOpenAPI()
    }
  }

  "OASParserImpl" should {
    "reading a simple OAS file gives one path" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
      val (result :: Nil) = parser.apply(sample) 
      
      result.uriPattern shouldBe "/hello/world"
      result.method shouldBe "GET"
      result.endpointName shouldBe "no endpoint name provided"
    }

    "reading a simple OAS file gives one path with summary" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        summary: A Summary
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
      val (result :: Nil) = parser.apply(sample) 
      
      result.uriPattern shouldBe "/hello/world"
      result.method shouldBe "GET"
      result.endpointName shouldBe "A Summary"
    }


    "reading a simple OAS file gives one path and two methods" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        responses:
        |          200:
        |            description: OK Response
        |      put:
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
        // Methods ordered alpahbetically
      val (get :: put :: Nil) = parser.apply(sample) 
      
      get.uriPattern shouldBe "/hello/world"
      put.uriPattern shouldBe "/hello/world"

      get.method shouldBe "GET"      
      put.method shouldBe "PUT"      
    }

    "reading a simple OAS file gives two paths each with one method" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        responses:
        |          200:
        |            description: OK Response
        |    /hello/user:
        |      put:
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
      val (get :: put :: Nil) = parser.apply(sample) 
      
      get.method shouldBe "GET"      
      get.uriPattern shouldBe "/hello/world"

      put.method shouldBe "PUT"      
      put.uriPattern shouldBe "/hello/user"
    }

    "reading a simple OAS file with query parameter" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        parameters: 
        |        - name: petId
        |          in: query
        |          required: true
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
      val (result :: Nil) = parser.apply(sample) 
      
      result.queryParameters shouldBe 'defined
      result.queryParameters.value.head shouldBe QueryParam("petId", true)
    }
    "reading a simple OAS file with multiple query parameters" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        parameters: 
        |        - name: petId
        |          in: query
        |          required: true
        |        - name: collarSize
        |          in: query
        |          required: false
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
      val (result :: Nil) = parser.apply(sample) 
      
      result.queryParameters shouldBe 'defined
      val (petId :: collarSize :: Nil) = result.queryParameters.value.toList
      petId shouldBe QueryParam("petId", true)
      collarSize shouldBe QueryParam("collarSize", false)
    }

    "reading a simple OAS file with multiple parameters but none are query params" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      get:
        |        parameters: 
        |        - name: petId
        |          in: header
        |          required: true
        |        - name: collarSize
        |          in: cookie
        |          required: false
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
      val (result :: Nil) = parser.apply(sample) 
      
      result.queryParameters shouldBe 'empty
    }

    "reading a simple OAS file gives one path and two methods with shared params" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      parameters:
        |        - name: petId
        |          in: query
        |          required: false
        |      get:
        |        parameters:
        |        - name: collarSize
        |          in: query
        |          required: false
        |        responses:
        |          200:
        |            description: OK Response
        |      put:
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
        // Methods ordered alpahbetically
      val (get :: put :: Nil) = parser.apply(sample) 

      get.queryParameters shouldBe 'defined  
      get.queryParameters.get should contain allOf (QueryParam("petId", false), QueryParam("collarSize", false))    

      put.queryParameters shouldBe 'defined  
      put.queryParameters.get should contain only QueryParam("petId", false)
    }

    "reading a simple OAS file gives one path and two methods with shared params and an override" in new Setup {
      val sample: OpenAPI = generate("""
        |  openapi: 3.0.3
        |
        |  info:
        |    version: 1.0.0
        |    title: Hello World
        |    
        |  paths:
        |    /hello/world:
        |      parameters:
        |        - name: petId
        |          in: query
        |          required: false
        |      get:
        |        parameters:
        |        - name: petId
        |          in: query
        |          required: true
        |        - name: collarSize
        |          in: query
        |          required: false
        |        responses:
        |          200:
        |            description: OK Response
        |      put:
        |        responses:
        |          200:
        |            description: OK Response
        |""".stripMargin)
      
        // Methods ordered alpahbetically
      val (get :: put :: Nil) = parser.apply(sample) 

      get.queryParameters shouldBe 'defined  
      get.queryParameters.get should contain allOf (QueryParam("petId", true), QueryParam("collarSize", false))    

      put.queryParameters shouldBe 'defined  
      put.queryParameters.get should contain only QueryParam("petId", false)
    }

  }
}
