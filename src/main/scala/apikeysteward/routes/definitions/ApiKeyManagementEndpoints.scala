package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.model.apikey._
import sttp.model.StatusCode
import sttp.tapir._

import java.util.UUID

private[routes] object ApiKeyManagementEndpoints {

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint
      : Endpoint[AccessToken, CreateApiKeyRequest, ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.createApiKeyEndpointBase
      .description("Create new API key.")
      .in("api-keys")

  val getAllApiKeysEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, GetMultipleApiKeysResponse), Any] =
    ApiKeyManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data.")
      .in("api-keys")

  val getSingleApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, GetSingleApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given key ID.")
      .in("api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key with given key ID.")
      .in("api-keys" / keyIdPathParameter)

}
