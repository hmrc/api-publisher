import scala.collection.immutable.ListMap
import scala.collection.mutable.Buffer
import java.util.function.BiConsumer
import java.util.LinkedHashMap
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions


    val parseOptions = new ParseOptions();
    parseOptions.setResolve(true);
    val emptyAuthList = java.util.Collections.emptyList[io.swagger.v3.parser.core.models.AuthorizationValue]()

    // val location = "/input/oas/application.yaml"
    val location = "/home/andys/projects/ee/hmrc/api-publisher/target/scala-2.12/test-classes/input/oas/application.yaml" //"/input/oas/application.yaml"
    val parserResult = new OpenAPIV3Parser().readLocation(location, emptyAuthList, parseOptions)

    parserResult.getMessages
    parserResult.getOpenAPI

  val x: LinkedHashMap[String, Int] = new LinkedHashMap()
  x.put("a", 1)
  x.put("b", 2)
  x.put("c", 3)

  val wrap = x

          val buffer = Buffer[(String, Int)]()
        val bc = new BiConsumer[String, Int] {
          def accept(k: String, v: Int): Unit = { buffer.append( (k,v) ) }
        }
        wrap.forEach(bc)

  buffer
  ListMap(buffer: _*)
  
  import uk.gov.hmrc.apipublisher.models.oas.SOpenAPI.Helpers._
  x.asScalaListMap