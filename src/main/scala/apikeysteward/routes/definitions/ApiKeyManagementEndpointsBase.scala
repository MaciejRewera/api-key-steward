package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.model.admin.apikey.{UpdateApiKeyAdminRequest, UpdateApiKeyAdminResponse}
import apikeysteward.routes.model.apikey._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[definitions] object ApiKeyManagementEndpointsBase {

  val createApiKeyEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, StatusCode, Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .out(statusCode.description(StatusCode.Created, "API key created"))
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllApiKeysForUserEndpointBase
      : Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, GetMultipleApiKeysResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .out(statusCode.description(StatusCode.Ok, "API keys found"))
      .out(
        jsonBody[GetMultipleApiKeysResponse]
          .example(
            GetMultipleApiKeysResponse(apiKeyData =
              List(EndpointsBase.ApiKeyDataExample, EndpointsBase.ApiKeyDataExample)
            )
          )
      )

  val getSingleApiKeyEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, GetSingleApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .out(statusCode.description(StatusCode.Ok, "API key found"))
      .out(
        jsonBody[GetSingleApiKeyResponse]
          .example(GetSingleApiKeyResponse(apiKeyData = EndpointsBase.ApiKeyDataExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteApiKeyEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .out(statusCode.description(StatusCode.Ok, "API key deleted"))
      .out(
        jsonBody[DeleteApiKeyResponse]
          .example(DeleteApiKeyResponse(EndpointsBase.ApiKeyDataExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

}