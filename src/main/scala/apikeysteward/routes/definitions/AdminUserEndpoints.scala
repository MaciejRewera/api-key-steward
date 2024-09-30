package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.model.admin.GetMultipleUserIdsResponse
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir._

private[routes] object AdminUserEndpoints {

  val getAllUserIdsEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, GetMultipleUserIdsResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all user IDs that have at least one API key.")
      .in("admin" / "users")
      .out(statusCode.description(StatusCode.Ok, "All user IDs found"))
      .out(
        jsonBody[GetMultipleUserIdsResponse]
          .example(GetMultipleUserIdsResponse(List("user-1234567", "user-1234568", "user-1234569")))
      )

}
