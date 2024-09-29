package apikeysteward.routes

import apikeysteward.routes.definitions.EndpointsBase.Tags
import apikeysteward.routes.definitions.{
  AdminApiKeyManagementEndpoints,
  ApiKeyManagementEndpoints,
  ApiKeyValidationEndpoints
}
import io.circe.syntax._
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe._
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object Documentation extends OpenAPIDocsInterpreter {

  private val adminEndpoints = List(
    AdminApiKeyManagementEndpoints.createApiKeyEndpoint,
    AdminApiKeyManagementEndpoints.getAllApiKeysForUserEndpoint,
    AdminApiKeyManagementEndpoints.getSingleApiKeyForUserEndpoint,
    AdminApiKeyManagementEndpoints.getAllUserIdsEndpoint,
    AdminApiKeyManagementEndpoints.deleteApiKeyEndpoint
  ).map(_.withTag(Tags.Admin))

  private val managementEndpoints = List(
    ApiKeyManagementEndpoints.createApiKeyEndpoint,
    ApiKeyManagementEndpoints.getAllApiKeysEndpoint,
    ApiKeyManagementEndpoints.getSingleApiKeyEndpoint,
    ApiKeyManagementEndpoints.deleteApiKeyEndpoint
  ).map(_.withTag(Tags.Management))

  private val validateApiKeyEndpoints =
    List(ApiKeyValidationEndpoints.validateApiKeyEndpoint)
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
