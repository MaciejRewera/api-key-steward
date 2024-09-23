package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.model.admin.{UpdateApiKeyRequest, UpdateApiKeyResponse}
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

object AdminEndpoints {

  private val userIdPathParameter = path[String]("userId").description("ID of the user.")

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val getAllUserIdsEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[String]), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all user IDs that have at least one API key.")
      .in("admin" / "users")
      .out(statusCode.description(StatusCode.Ok, "All user IDs found"))
      .out(
        jsonBody[List[String]]
          .example(List("user-1234567", "user-1234568", "user-1234569"))
      )

  val createApiKeyEndpoint
      : Endpoint[AccessToken, (CreateApiKeyRequest, String), ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    ManagementEndpointsBase.createApiKeyEndpointBase
      .description("Create new API key for given user ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys")

  val updateApiKeyEndpoint
      : Endpoint[AccessToken, (UpdateApiKeyRequest, String, UUID), ErrorInfo, (StatusCode, UpdateApiKeyResponse), Any] =
    ManagementEndpointsBase.updateApiKeyEndpointBase
      .description("Update API key for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

  val getAllApiKeysForUserEndpoint: Endpoint[AccessToken, String, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    ManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data for given user ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys")

  val getSingleApiKeyForUserEndpoint: Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, ApiKeyData), Any] =
    ManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint: Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

}
