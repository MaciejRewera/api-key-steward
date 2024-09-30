package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.model.{ValidateApiKeyRequest, ValidateApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object ApiKeyValidationEndpoints {

  val validateApiKeyEndpoint
      : Endpoint[Unit, ValidateApiKeyRequest, ErrorInfo, (StatusCode, ValidateApiKeyResponse), Any] =
    endpoint.post
      .description(
        "Validate API key. This API is public - it does not require either authentication nor authorization."
      )
      .in("api-keys" / "validation")
      .in(
        jsonBody[ValidateApiKeyRequest]
          .description("API key to validate.")
          .example(ValidateApiKeyRequest(apiKey = EndpointsBase.ApiKeyExample.value))
      )
      .out(statusCode.description(StatusCode.Ok, "API key is valid."))
      .out(
        jsonBody[ValidateApiKeyResponse]
          .description("All data related to provided API key.")
          .example(ValidateApiKeyResponse(EndpointsBase.ApiKeyDataExample))
      )
      .errorOut(
        oneOf[ErrorInfo](
          errorOutVariantInternalServerError,
          errorOutVariantForbidden,
          errorOutVariantBadRequest
        )
      )
}
