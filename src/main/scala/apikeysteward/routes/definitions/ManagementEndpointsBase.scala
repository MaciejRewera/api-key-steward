package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.model.admin.{UpdateApiKeyRequest, UpdateApiKeyResponse}
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.ApiKeyExpirationCalculator
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[definitions] object ManagementEndpointsBase {

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
        jsonBody[CreateApiKeyResponse].example(
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
            s"Details of the API key to create. The time unit of 'ttl' parameter are ${ApiKeyExpirationCalculator.ttlTimeUnit.toString.toLowerCase}."
          )
          .example(
            UpdateApiKeyRequest(
              name = "My API key",
              description = Some("A short description what this API key is for."),
              ttl = 60
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "API key created"))
      .out(
        jsonBody[UpdateApiKeyResponse].example(
          UpdateApiKeyResponse(
            apiKeyData = EndpointsBase.ApiKeyDataExample
          )
        )
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllApiKeysForUserEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .out(statusCode.description(StatusCode.Ok, "API keys found"))
      .out(
        jsonBody[List[ApiKeyData]]
          .example(List(EndpointsBase.ApiKeyDataExample, EndpointsBase.ApiKeyDataExample))
      )

  val getSingleApiKeyEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, ApiKeyData), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .out(statusCode.description(StatusCode.Ok, "API key found"))
      .out(
        jsonBody[ApiKeyData]
          .example(EndpointsBase.ApiKeyDataExample)
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
