package apikeysteward.routes

import apikeysteward.routes.definitions.EndpointsBase.Tags
import apikeysteward.routes.definitions.{AdminEndpoints, ManagementEndpoints, ValidateApiKeyEndpoints}
import io.circe.syntax._
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
  ).map(_.withTag(Tags.Admin))

  private val managementEndpoints = List(
    ManagementEndpoints.createApiKeyEndpoint,
    ManagementEndpoints.getAllApiKeysEndpoint,
    ManagementEndpoints.deleteApiKeyEndpoint
  ).map(_.withTag(Tags.Public))

  private val validateApiKeyEndpoints =
    List(ValidateApiKeyEndpoints.validateApiKeyEndpoint)
      .map(_.withTag(Tags.Public))

  private val allEndpoints = adminEndpoints ++ managementEndpoints ++ validateApiKeyEndpoints

  private val allOpenApiDocs: OpenAPI = toOpenAPI(
    allEndpoints,
    title = "API Key Steward documentation",
    version = "0.1.0"
  )

  val allJsonDocs: String = allOpenApiDocs.asJson.deepDropNullValues.toString
  val allYamlDocs: String = allOpenApiDocs.toYaml
}
