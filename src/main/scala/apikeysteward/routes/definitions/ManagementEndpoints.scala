package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtValidator.AccessToken
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

object ManagementEndpoints {

  private val keyIdPathParameter = path[UUID]("keyId").description("ID of the API Key.")

  val createApiKeyEndpoint
      : Endpoint[AccessToken, CreateApiKeyRequest, ErrorInfo, (StatusCode, CreateApiKeyResponse), Any] =
    ManagementEndpointsBase.createApiKeyEndpointBase
      .in("api-key")

  val getAllApiKeysEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, List[ApiKeyData]), Any] =
    ManagementEndpointsBase.getAllApiKeysForUserEndpointBase
      .in("api-key")

  val deleteApiKeyEndpoint: Endpoint[AccessToken, UUID, ErrorInfo, (StatusCode, DeleteApiKeyResponse), Any] =
    ManagementEndpointsBase.deleteApiKeyEndpointBase
      .in("api-key" / keyIdPathParameter)
      .errorOutVariantPrepend(
        oneOfVariantExactMatcher(
          StatusCode.NotFound,
          jsonBody[ErrorInfo].description(ApiErrorMessages.Management.DeleteApiKeyNotFound)
        )(ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.DeleteApiKeyNotFound)))
      )

}
