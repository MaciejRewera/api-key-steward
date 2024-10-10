package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.model.apikey._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

private[definitions] object ApiKeyManagementEndpointsBase {

  val keyIdPathParameter: EndpointInput.PathCapture[UUID] = path[UUID]("keyId").description("Unique ID of the API Key.")

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
