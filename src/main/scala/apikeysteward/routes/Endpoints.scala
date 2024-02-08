package apikeysteward.routes

import apikeysteward.routes.ErrorInfo.CommonErrorInfo
import apikeysteward.routes.model.{
  CreateApiKeyRequest,
  CreateApiKeyResponse,
  ValidateApiKeyRequest,
  ValidateApiKeyResponse
}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object Endpoints {

  private val baseEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.in("api-key")

  val createApiKeyEndpoint: PublicEndpoint[CreateApiKeyRequest, Unit, (StatusCode, CreateApiKeyResponse), Any] =
    baseEndpoint.post
      .in("create")
      .in(
        jsonBody[CreateApiKeyRequest]
          .description("Details of the API Key to create.")
      )
      .out(statusCode.description(StatusCode.Created, "API Key created"))
      .out(jsonBody[CreateApiKeyResponse])

  val validateApiKeyEndpoint
      : PublicEndpoint[ValidateApiKeyRequest, ErrorInfo, (StatusCode, ValidateApiKeyResponse), Any] =
    baseEndpoint.post
      .in("validate")
      .in(
        jsonBody[ValidateApiKeyRequest]
          .description("API Key to validate.")
      )
      .out(statusCode.description(StatusCode.Ok, "API Key is valid."))
      .out(jsonBody[ValidateApiKeyResponse])
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(
            StatusCode.Forbidden,
            jsonBody[CommonErrorInfo]
              .description("Provided API Key is incorrect or does not exist.")
          )
        )
      )

}
