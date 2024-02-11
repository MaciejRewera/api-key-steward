package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.ErrorInfo.CommonErrorInfo
import apikeysteward.routes.definitions.EndpointUtils.AccessToken
import apikeysteward.routes.model.admin._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object AdminEndpoints {

  object ErrorMessages {
    val DeleteApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
    val GetAllApiKeysForUserNotFound = "No API Key found for provided userId."
  }

  private val baseAdminEndpoint: Endpoint[AccessToken, Unit, Unit, Unit, Any] =
    endpoint.in("admin")

  private val userIdPathParameter =
    path[String]("userId").description("ID of the user for which to retrieve all API Keys information.")

  val getAllApiKeysForUserEndpoint: Endpoint[AccessToken, String, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    baseAdminEndpoint.get
      .in("users" / userIdPathParameter / "api-keys")
      .out(statusCode.description(StatusCode.Ok, "API Keys found for provided userId."))
      .out(jsonBody[List[ApiKeyData]])
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(
            StatusCode.NotFound,
            jsonBody[CommonErrorInfo].description(ErrorMessages.GetAllApiKeysForUserNotFound)
          )
        )
      )

  val getAllUserIdsEndpoint: Endpoint[AccessToken, Unit, Unit, (StatusCode, List[String]), Any] =
    baseAdminEndpoint.get
      .in("users")
      .out(statusCode.description(StatusCode.Ok, "All user IDs found."))
      .out(jsonBody[List[String]])

  val createApiKeyEndpoint
      : Endpoint[AccessToken, CreateApiKeyAdminRequest, Unit, (StatusCode, CreateApiKeyAdminResponse), Any] =
    baseAdminEndpoint.post
      .in("api-key")
      .in(
        jsonBody[CreateApiKeyAdminRequest]
          .description("Details of the API Key to create.")
      )
      .out(statusCode.description(StatusCode.Created, "API Key created"))
      .out(jsonBody[CreateApiKeyAdminResponse])

  val deleteApiKeyEndpoint
      : Endpoint[AccessToken, DeleteApiKeyAdminRequest, ErrorInfo, (StatusCode, DeleteApiKeyAdminResponse), Any] =
    baseAdminEndpoint.delete
      .in("api-key")
      .in(
        jsonBody[DeleteApiKeyAdminRequest]
          .description("Details of the API Key to delete.")
      )
      .out(statusCode.description(StatusCode.Ok, "API Key deleted"))
      .out(jsonBody[DeleteApiKeyAdminResponse])
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(
            StatusCode.NotFound,
            jsonBody[CommonErrorInfo].description(ErrorMessages.DeleteApiKeyNotFound)
          )
        )
      )

}
