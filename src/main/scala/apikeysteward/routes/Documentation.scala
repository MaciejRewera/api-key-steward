package apikeysteward.routes

import apikeysteward.routes.definitions.{AdminEndpoints, ManagementEndpoints, ValidateApiKeyEndpoints}
import io.circe.syntax._
import org.http4s.BuildInfo
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe._
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object Documentation extends OpenAPIDocsInterpreter {

  private val adminEndpoints = List(
    AdminEndpoints.createApiKeyEndpoint,
    AdminEndpoints.getAllApiKeysForUserEndpoint,
    AdminEndpoints.getAllUserIdsEndpoint,
    AdminEndpoints.deleteApiKeyEndpoint
  )

  private val managementEndpoints = List(
    ManagementEndpoints.createApiKeyEndpoint,
    ManagementEndpoints.getAllApiKeysEndpoint,
    ManagementEndpoints.deleteApiKeyEndpoint
  )

  private val validateApiKeyEndpoints = List(ValidateApiKeyEndpoints.validateApiKeyEndpoint)

  private val allEndpoints = adminEndpoints ++ managementEndpoints ++ validateApiKeyEndpoints

  private val allOpenApiDocs: OpenAPI = toOpenAPI(
    allEndpoints,
    title = "API Key Steward documentation",
    version = s"${BuildInfo.version}"
  )

  val allJsonDocs: String = allOpenApiDocs.asJson.deepDropNullValues.toString
  val allYamlDocs: String = allOpenApiDocs.toYaml
}
