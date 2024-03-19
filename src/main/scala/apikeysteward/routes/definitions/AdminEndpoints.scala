package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.ErrorInfo.CommonErrorInfo
import apikeysteward.routes.definitions.EndpointUtils.AccessToken
import apikeysteward.routes.model.admin.{CreateApiKeyAdminRequest, _}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

object AdminEndpoints {

  object ErrorMessages {
    val DeleteApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
    val GetAllApiKeysForUserNotFound = "No API Key found for provided userId."
  }

  private val baseAdminEndpoint: Endpoint[AccessToken, Unit, Unit, Unit, Any] =
    endpoint.in("admin")

  private val userIdPathParameter = path[String]("userId").description("ID of the user.")

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint
      : Endpoint[AccessToken, (String, CreateApiKeyAdminRequest), Unit, (StatusCode, CreateApiKeyAdminResponse), Any] =
    baseAdminEndpoint.post
      .in("users" / userIdPathParameter / "api-key")
      .in(
        jsonBody[CreateApiKeyAdminRequest]
          .description("Details of the API Key to create.")
      )
      .out(statusCode.description(StatusCode.Created, "API Key created"))
      .out(jsonBody[CreateApiKeyAdminResponse])

  val getAllUserIdsEndpoint: Endpoint[AccessToken, Unit, Unit, (StatusCode, List[String]), Any] =
    baseAdminEndpoint.get
      .in("users")
      .out(statusCode.description(StatusCode.Ok, "All user IDs found."))
      .out(jsonBody[List[String]])

  val getAllApiKeysForUserEndpoint: Endpoint[AccessToken, String, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    baseAdminEndpoint.get
      .in("users" / userIdPathParameter / "api-key")
      .out(statusCode.description(StatusCode.Ok, "API Keys found for provided userId."))
      .out(jsonBody[List[ApiKeyData]])
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(
            StatusCode.NotFound,
            jsonBody[ErrorInfo].description(ErrorMessages.GetAllApiKeysForUserNotFound)
          )
        )
      )

  val deleteApiKeyEndpoint
      : Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, DeleteApiKeyAdminResponse), Any] =
    baseAdminEndpoint.delete
      .in("users" / userIdPathParameter / "api-key" / keyIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "API Key deleted"))
      .out(jsonBody[DeleteApiKeyAdminResponse])
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(
            StatusCode.NotFound,
            jsonBody[ErrorInfo].description(ErrorMessages.DeleteApiKeyNotFound)
          )
        )
      )

}
