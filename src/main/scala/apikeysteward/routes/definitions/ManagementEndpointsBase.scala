package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
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
          .description("Details of the API key to create.")
          .example(
            CreateApiKeyRequest(
              name = "My API key",
              description = Some("A short description what this API key is for."),
              ttl = 3600,
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

  val getAllApiKeysForUserEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .out(statusCode.description(StatusCode.Ok, "API keys found"))
      .out(
        jsonBody[List[ApiKeyData]]
          .example(List(EndpointsBase.ApiKeyDataExample, EndpointsBase.ApiKeyDataExample))
      )

  val deleteApiKeyEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .out(statusCode.description(StatusCode.Ok, "API key deleted"))
      .out(
        jsonBody[DeleteApiKeyResponse]
          .example(DeleteApiKeyResponse(EndpointsBase.ApiKeyDataExample))
      )

}
