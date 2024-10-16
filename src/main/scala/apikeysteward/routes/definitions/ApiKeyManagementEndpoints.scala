package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.ApiKeyManagementEndpointsBase.keyIdPathParameter
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.errorOutVariantBadRequest
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyExpirationCalculator
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

private[routes] object ApiKeyManagementEndpoints {

  val createApiKeyEndpoint
      : Endpoint[AccessToken, CreateApiKeyRequest, ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create new API key.")
      .in("api-keys")
      .in(
        jsonBody[CreateApiKeyRequest]
          .description(
            s"Details of the API key to create. The time unit of 'ttl' parameter are ${ApiKeyExpirationCalculator.TtlTimeUnit.toString.toLowerCase}."
          )
          .example(
            CreateApiKeyRequest(
              name = "My API key",
              description = Some("A short description what this API key is for."),
              ttl = 60
            )
          )
      )
      .out(statusCode.description(StatusCode.Created, "API key created successfully"))
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

  val getAllApiKeysEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, GetMultipleApiKeysResponse), Any] =
    ApiKeyManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data.")
      .in("api-keys")

  val getSingleApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, GetSingleApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given key ID.")
      .in("api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key with given key ID.")
      .in("api-keys" / keyIdPathParameter)

}
