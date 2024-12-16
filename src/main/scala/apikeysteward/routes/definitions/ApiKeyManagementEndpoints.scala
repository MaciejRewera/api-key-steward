package apikeysteward.routes.definitions

import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.ApiKeyManagementEndpointsBase.keyIdPathParameter
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.definitions.EndpointsBase.tenantIdHeaderInput
import apikeysteward.routes.model.apikey._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

private[routes] object ApiKeyManagementEndpoints {

  val createApiKeyEndpoint
      : Endpoint[AccessToken, (TenantId, CreateApiKeyRequest), ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create new API key.")
      .in(tenantIdHeaderInput)
      .in("api-keys")
      .in(
        jsonBody[CreateApiKeyRequest]
          .description(s"Details of the API key to create.")
          .example(EndpointsBase.CreateApiKeyAdminRequestExample.toUserRequest._2)
      )
      .out(statusCode.description(StatusCode.Created, "API key created successfully"))
      .out(
        jsonBody[CreateApiKeyResponse]
          .example(
            CreateApiKeyResponse(
              apiKey = EndpointsBase.ApiKeyExample.value,
              apiKeyData = EndpointsBase.ApiKeyDataExample
            )
          )
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllApiKeysEndpoint: Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, GetMultipleApiKeysResponse), Any] =
    ApiKeyManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data.")
      .in(tenantIdHeaderInput)
      .in("api-keys")
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleApiKeyEndpoint
      : Endpoint[AccessToken, (TenantId, UUID), ErrorInfo, (StatusCode, GetSingleApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given key ID.")
      .in(tenantIdHeaderInput)
      .in("api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint
      : Endpoint[AccessToken, (TenantId, UUID), ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key with given key ID.")
      .in(tenantIdHeaderInput)
      .in("api-keys" / keyIdPathParameter)

}
