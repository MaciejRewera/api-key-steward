package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.model.admin.apikey.{
  CreateApiKeyAdminRequest,
  CreateApiKeyAdminResponse,
  UpdateApiKeyAdminRequest,
  UpdateApiKeyAdminResponse
}
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyExpirationCalculator
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

private[routes] object AdminApiKeyManagementEndpoints {

  private val userIdPathParameter = path[String]("userId").description("ID of the user.")

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint
      : Endpoint[AccessToken, CreateApiKeyAdminRequest, ErrorInfo, (StatusCode, CreateApiKeyAdminResponse), Any] =
    ApiKeyManagementEndpointsBase.createApiKeyEndpointBase
      .description("Create new API key for a user.")
      .in("admin" / "api-keys")
      .in(
        jsonBody[CreateApiKeyAdminRequest]
          .description(
            s"Details of the API key to create. The time unit of 'ttl' parameter are ${ApiKeyExpirationCalculator.TtlTimeUnit.toString.toLowerCase}."
          )
          .example(
            CreateApiKeyAdminRequest(
              userId = "user-1234567890",
              name = "My API key",
              description = Some("A short description what this API key is for."),
              ttl = 60,
              scopes = List("read:myApi", "write:myApi")
            )
          )
      )
      .out(
        jsonBody[CreateApiKeyAdminResponse]
          .example(
            CreateApiKeyAdminResponse(
              apiKey = EndpointsBase.ApiKeyExample.value,
              apiKeyData = EndpointsBase.ApiKeyDataExample
            )
          )
      )

  val updateApiKeyEndpoint: Endpoint[
    AccessToken,
    (String, UUID, UpdateApiKeyAdminRequest),
    ErrorInfo,
    (StatusCode, UpdateApiKeyAdminResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.put
      .description("Update API key for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)
      .in(
        jsonBody[UpdateApiKeyAdminRequest]
          .description(
            """Details of the API key to update. You have to specify all of the fields of the API key data.
              |This API replaces the existing API key's data with your new data.""".stripMargin
          )
          .example(
            UpdateApiKeyAdminRequest(
              name = "My API key",
              description = Some("A short description what this API key is for.")
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "API key updated"))
      .out(
        jsonBody[UpdateApiKeyAdminResponse].example(
          UpdateApiKeyAdminResponse(
            apiKeyData = EndpointsBase.ApiKeyDataExample
          )
        )
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllApiKeysForUserEndpoint
      : Endpoint[AccessToken, String, ErrorInfo, (StatusCode, GetMultipleApiKeysResponse), Any] =
    ApiKeyManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .description("Get all API keys data for given user ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys")

  val getSingleApiKeyForUserEndpoint
      : Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, GetSingleApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.getSingleApiKeyEndpointBase
      .description("Get API key data for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

  val deleteApiKeyEndpoint: Endpoint[AccessToken, (String, UUID), ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ApiKeyManagementEndpointsBase.deleteApiKeyEndpointBase
      .description("Delete API key for given user ID and key ID.")
      .in("admin" / "users" / userIdPathParameter / "api-keys" / keyIdPathParameter)

}