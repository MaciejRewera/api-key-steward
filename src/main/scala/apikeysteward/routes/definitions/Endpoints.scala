package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.model.{ValidateApiKeyRequest, ValidateApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object Endpoints {

  object ErrorMessages {
    val ValidateApiKeyIncorrect = "Provided API Key is incorrect or does not exist."
  }

  private val baseEndpoint: Endpoint[Unit, Unit, Unit, Unit, Any] =
    endpoint.in("api-key")

  val validateApiKeyEndpoint
      : Endpoint[Unit, ValidateApiKeyRequest, ErrorInfo, (StatusCode, ValidateApiKeyResponse), Any] =
    baseEndpoint.post
      .in("validation")
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
            jsonBody[ErrorInfo]
              .description(ErrorMessages.ValidateApiKeyIncorrect)
          )
        )
      )
}
