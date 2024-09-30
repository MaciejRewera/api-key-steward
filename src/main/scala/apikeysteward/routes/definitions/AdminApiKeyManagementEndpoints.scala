package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.model.admin.{UpdateApiKeyRequest, UpdateApiKeyResponse}
import apikeysteward.routes.model.apikey._
import sttp.model.StatusCode
import sttp.tapir._

import java.util.UUID

private[routes] object AdminApiKeyManagementEndpoints {

  private val userIdPathParameter = path[String]("userId").description("ID of the user.")

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint
      : Endpoint[AccessToken, (CreateApiKeyRequest, String), ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.createApiKeyEndpointBase
      .description("Create new API key for given user ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys")

  val updateApiKeyEndpoint
      : Endpoint[AccessToken, (UpdateApiKeyRequest, String, UUID), ErrorInfo, (StatusCode, UpdateApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.updateApiKeyEndpointBase
      .description("Update API key for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

  val getAllApiKeysForUserEndpoint
      : Endpoint[AccessToken, String, ErrorInfo, (StatusCode, GetMultipleApiKeysResponse), Any] =
    ApiKeyManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data for given user ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys")

  val getSingleApiKeyForUserEndpoint
      : Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, GetSingleApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint: Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

}
