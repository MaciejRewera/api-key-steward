package apikeysteward.routes

import apikeysteward.routes.definitions._
import io.circe.syntax._
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe._
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object Documentation extends OpenAPIDocsInterpreter {

  private val adminApiKeyManagementEndpoints = List(
    AdminApiKeyManagementEndpoints.createApiKeyEndpoint,
    AdminApiKeyManagementEndpoints.getAllApiKeysForUserEndpoint,
    AdminApiKeyManagementEndpoints.getSingleApiKeyEndpoint,
    AdminApiKeyManagementEndpoints.deleteApiKeyEndpoint
  ).map(_.withTag(Tags.AdminApiKeys))

  private val adminTenantEndpoints = List(
    AdminTenantEndpoints.createTenantEndpoint,
    AdminTenantEndpoints.updateTenantEndpoint,
    AdminTenantEndpoints.reactivateTenantEndpoint,
    AdminTenantEndpoints.deactivateTenantEndpoint,
    AdminTenantEndpoints.deleteTenantEndpoint,
    AdminTenantEndpoints.getSingleTenantEndpoint,
    AdminTenantEndpoints.getAllTenantsEndpoint
  ).map(_.withTag(Tags.AdminTenants))

  private val adminUserEndpoints = List(
    AdminUserEndpoints.getAllUserIdsEndpoint
  ).map(_.withTag(Tags.AdminUsers))

  private val managementEndpoints = List(
    ApiKeyManagementEndpoints.createApiKeyEndpoint,
    ApiKeyManagementEndpoints.getAllApiKeysEndpoint,
    ApiKeyManagementEndpoints.getSingleApiKeyEndpoint,
    ApiKeyManagementEndpoints.deleteApiKeyEndpoint
  ).map(_.withTag(Tags.UserApiKeys))

  private val validateApiKeyEndpoints =
    List(ApiKeyValidationEndpoints.validateApiKeyEndpoint)
      .map(_.withTag(Tags.Public))

  private val allEndpoints =
    adminApiKeyManagementEndpoints ++
      adminTenantEndpoints ++
      adminUserEndpoints ++
      managementEndpoints ++
      validateApiKeyEndpoints

  private val allOpenApiDocs: OpenAPI = toOpenAPI(
    allEndpoints,
    title = "API Key Steward documentation",
    version = "0.1.0"
  )

  val allJsonDocs: String = allOpenApiDocs.asJson.deepDropNullValues.toString
  val allYamlDocs: String = allOpenApiDocs.toYaml

  private object Tags {
    val AdminApiKeys = "API keys"
    val AdminTenants = "Tenants"
    val AdminUsers = "Users"

    val UserApiKeys = "User - API keys"
    val Public = "Public"
  }

}
