package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._

import java.util.UUID

object ManagementEndpoints {

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint
      : Endpoint[AccessToken, CreateApiKeyRequest, ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    ManagementEndpointsBase.createApiKeyEndpointBase
      .description("Create new API key.")
      .in("api-keys")

  val getAllApiKeysEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    ManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data.")
      .in("api-keys")

  val getSingleApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, ApiKeyData), Any] =
    ManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given key ID.")
      .in("api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key with given key ID.")
      .in("api-keys" / keyIdPathParameter)

}
