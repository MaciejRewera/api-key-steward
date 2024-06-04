package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtValidator.AccessToken
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[definitions] object ManagementEndpointsBase {

  val authenticatedEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, Unit, Any] =
    endpoint
      .securityIn(auth.bearer[AccessToken]())
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariantExactMatcher(
            StatusCode.InternalServerError,
            jsonBody[ErrorInfo].description("An unexpected error has occurred.")
          )(ErrorInfo.internalServerErrorInfo()),
          oneOfVariant(
            StatusCode.Unauthorized,
            jsonBody[ErrorInfo].description(ApiErrorMessages.General.Unauthorized)
          )
        )
      )

  val createApiKeyEndpointBase
      : Endpoint[AccessToken, CreateApiKeyRequest, ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    authenticatedEndpointBase.post
      .in(
        jsonBody[CreateApiKeyRequest]
          .description("Details of the API Key to create.")
      )
      .out(statusCode.description(StatusCode.Created, "API Key created"))
      .out(jsonBody[CreateApiKeyResponse])

  val getAllApiKeysForUserEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    authenticatedEndpointBase.get
      .out(statusCode.description(StatusCode.Ok, "API Keys found"))
      .out(jsonBody[List[ApiKeyData]])

  val deleteApiKeyEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    authenticatedEndpointBase.delete
      .out(statusCode.description(StatusCode.Ok, "API Key deleted"))
      .out(jsonBody[DeleteApiKeyResponse])

}
