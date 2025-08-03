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
    AdminApiKeyManagementEndpoints.getSingleApiKeyEndpoint,
    AdminApiKeyManagementEndpoints.deleteApiKeyEndpoint
  ).map(_.withTag(Tags.AdminApiKeys))

  private val adminApiKeyTemplateEndpoints = List(
    AdminApiKeyTemplateEndpoints.createApiKeyTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.updateApiKeyTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.deleteResourceServerEndpoint,
    AdminApiKeyTemplateEndpoints.getSingleApiKeyTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.searchApiKeyTemplatesEndpoint,
    AdminApiKeyTemplateEndpoints.associatePermissionsWithApiKeyTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.removePermissionFromApiKeyTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.getAllPermissionsForTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.associateUsersWithApiKeyTemplateEndpoint,
    AdminApiKeyTemplateEndpoints.removeUserFromApiKeyTemplatesEndpoint,
    AdminApiKeyTemplateEndpoints.getAllUsersForTemplateEndpoint
  ).map(_.withTag(Tags.AdminApiKeyTemplates))

  private val adminTenantEndpoints = List(
    AdminTenantEndpoints.createTenantEndpoint,
    AdminTenantEndpoints.updateTenantEndpoint,
    AdminTenantEndpoints.reactivateTenantEndpoint,
    AdminTenantEndpoints.deactivateTenantEndpoint,
    AdminTenantEndpoints.deleteTenantEndpoint,
    AdminTenantEndpoints.getSingleTenantEndpoint,
    AdminTenantEndpoints.getAllTenantsEndpoint
  ).map(_.withTag(Tags.AdminTenants))

  private val adminResourceServerEndpoints = List(
    AdminResourceServerEndpoints.createResourceServerEndpoint,
    AdminResourceServerEndpoints.updateResourceServerEndpoint,
    AdminResourceServerEndpoints.deleteResourceServerEndpoint,
    AdminResourceServerEndpoints.getSingleResourceServerEndpoint,
    AdminResourceServerEndpoints.searchResourceServersEndpoint
  ).map(_.withTag(Tags.AdminResourceServers))

  private val adminPermissionEndpoints = List(
    AdminPermissionEndpoints.createPermissionEndpoint,
    AdminPermissionEndpoints.deletePermissionEndpoint,
    AdminPermissionEndpoints.getSinglePermissionEndpoint,
    AdminPermissionEndpoints.searchPermissionsEndpoint
  ).map(_.withTag(Tags.AdminPermissions))

  private val adminUserEndpoints = List(
    AdminUserEndpoints.createUserEndpoint,
    AdminUserEndpoints.deleteUserEndpoint,
    AdminUserEndpoints.getSingleUserEndpoint,
    AdminUserEndpoints.getAllUsersForTenantEndpoint,
    AdminUserEndpoints.associateApiKeyTemplatesWithUserEndpoint,
    AdminUserEndpoints.removeApiKeyTemplateFromUserEndpoint,
    AdminUserEndpoints.getAllApiKeyTemplatesForUserEndpoint,
    AdminUserEndpoints.getAllApiKeysForUserEndpoint
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
      adminApiKeyTemplateEndpoints ++
      adminTenantEndpoints ++
      adminResourceServerEndpoints ++
      adminPermissionEndpoints ++
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
    val AdminApiKeys         = "API keys"
    val AdminApiKeyTemplates = "Templates"
    val AdminTenants         = "Tenants"
    val AdminResourceServers = "ResourceServers"
    val AdminPermissions     = "Permissions"
    val AdminUsers           = "Users"

    val UserApiKeys = "User - API keys"
    val Public      = "Public"
  }

}
