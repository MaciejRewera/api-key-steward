package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.{errorOutVariantBadRequest, errorOutVariantNotFound}
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
      .in("admin" / "users")
      .out(statusCode.description(StatusCode.Ok, "All user IDs found"))
      .out(jsonBody[List[String]])

  val createApiKeyEndpoint
      : Endpoint[AccessToken, (CreateApiKeyRequest, String), ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    ManagementEndpointsBase.createApiKeyEndpointBase
      .in("admin" / "users" / userIdPathParameter / "api-key")
      .errorOutVariant(errorOutVariantBadRequest)

  val getAllApiKeysForUserEndpoint: Endpoint[AccessToken, String, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    ManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .in("admin" / "users" / userIdPathParameter / "api-key")

  val deleteApiKeyEndpoint: Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ManagementEndpointsBase.deleteApiKeyEndpointBase
      .in("admin" / "users" / userIdPathParameter / "api-key" / keyIdPathParameter)
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariant(errorOutVariantBadRequest)

}
