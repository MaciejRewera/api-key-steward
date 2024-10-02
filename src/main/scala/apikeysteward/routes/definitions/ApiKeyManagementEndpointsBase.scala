package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.model.admin.{UpdateApiKeyRequest, UpdateApiKeyResponse}
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyExpirationCalculator
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[definitions] object ApiKeyManagementEndpointsBase {

  val createApiKeyEndpointBase
      : Endpoint[AccessToken, CreateApiKeyRequest, ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .in(
        jsonBody[CreateApiKeyRequest]
          .description(
            s"Details of the API key to create. The time unit of 'ttl' parameter are ${ApiKeyExpirationCalculator.ttlTimeUnit.toString.toLowerCase}."
          )
          .example(
            CreateApiKeyRequest(
              name = "My API key",
              description = Some("A short description what this API key is for."),
              ttl = 60,
              scopes = List("read:myApi", "write:myApi")
            )
          )
      )
      .out(statusCode.description(StatusCode.Created, "API key created"))
      .out(
        jsonBody[CreateApiKeyResponse]
          .example(
            CreateApiKeyResponse(
              apiKey = EndpointsBase.ApiKeyExample.value,
              apiKeyData = EndpointsBase.ApiKeyDataExample
            )
          )
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val updateApiKeyEndpointBase
      : Endpoint[AccessToken, UpdateApiKeyRequest, ErrorInfo, (StatusCode, UpdateApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .in(
        jsonBody[UpdateApiKeyRequest]
          .description(
            """Details of the API key to update. You have to specify all of the fields of the API key data.
              |This API replaces the existing API key's data with your new data.""".stripMargin
          )
          .example(
            UpdateApiKeyRequest(
              name = "My API key",
              description = Some("A short description what this API key is for.")
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "API key updated"))
      .out(
        jsonBody[UpdateApiKeyResponse].example(
          UpdateApiKeyResponse(
            apiKeyData = EndpointsBase.ApiKeyDataExample
          )
        )
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
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
