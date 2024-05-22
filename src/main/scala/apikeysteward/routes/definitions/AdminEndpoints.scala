package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtValidator.AccessToken
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

object AdminEndpoints {

  object ErrorMessages {
    val DeleteApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
    val GetAllApiKeysForUserNotFound = "No API Key found for provided userId."
    val Unauthorized = "Credentials are invalid."
  }

  private val userIdPathParameter = path[String]("userId").description("ID of the user.")

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint: Endpoint[
    AccessToken,
    (CreateApiKeyRequest, String),
    ErrorInfo,
    (StatusCode, CreateApiKeyResponse),
    Any
  ] =
    ManagementEndpointsBase.createApiKeyEndpointBase
      .in("admin" / "users" / userIdPathParameter / "api-key")

  val getAllUserIdsEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[String]), Any] =
    ManagementEndpointsBase.authenticatedEndpointBase.get
      .in("admin" / "users")
      .out(statusCode.description(StatusCode.Ok, "All user IDs found."))
      .out(jsonBody[List[String]])
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(
            StatusCode.Unauthorized,
            jsonBody[ErrorInfo].description(ErrorMessages.Unauthorized)
          )
        )
      )

  val getAllApiKeysForUserEndpoint: Endpoint[AccessToken, String, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    ManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .in("admin" / "users" / userIdPathParameter / "api-key")

  val deleteApiKeyEndpoint: Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ManagementEndpointsBase.deleteApiKeyEndpointBase
      .in("admin" / "users" / userIdPathParameter / "api-key" / keyIdPathParameter)

}
