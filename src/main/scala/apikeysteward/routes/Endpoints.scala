package apikeysteward.routes

import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object Endpoints {

  private val baseEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.in("api-key" / "create")

  val createApiKeyEndpoint: PublicEndpoint[CreateApiKeyRequest, Unit, (StatusCode, CreateApiKeyResponse), Any] =
    baseEndpoint.post
      .in(
        jsonBody[CreateApiKeyRequest]
          .description("Details of the API Key to create.")
      )
      .out(statusCode.description(StatusCode.Created, "API Key created"))
      .out(jsonBody[CreateApiKeyResponse])

}
