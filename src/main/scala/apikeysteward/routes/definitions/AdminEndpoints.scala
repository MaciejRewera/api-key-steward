package apikeysteward.routes.definitions

import apikeysteward.routes.model.admin._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object AdminEndpoints {

  private val baseAdminEndpoint: Endpoint[Unit, Unit, Unit, Unit, Any] =
    endpoint.in("admin" / "api-key")

  val createApiKeyEndpoint
      : PublicEndpoint[CreateApiKeyAdminRequest, Unit, (StatusCode, CreateApiKeyAdminResponse), Any] =
    baseAdminEndpoint.post
      .in("create")
      .in(
        jsonBody[CreateApiKeyAdminRequest]
          .description("Details of the API Key to create.")
      )
      .out(statusCode.description(StatusCode.Created, "API Key created"))
      .out(jsonBody[CreateApiKeyAdminResponse])

}
