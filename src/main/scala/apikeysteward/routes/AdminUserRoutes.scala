package apikeysteward.routes

import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.AdminUserEndpoints
import apikeysteward.routes.model.admin.GetMultipleUserIdsResponse
import apikeysteward.services.ApiKeyManagementService
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminUserRoutes(jwtAuthorizer: JwtAuthorizer, managementService: ApiKeyManagementService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val getAllUserIdsRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminUserEndpoints.getAllUserIdsEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => _ =>
            managementService.getAllUserIds
              .map(allUserIds => (StatusCode.Ok -> GetMultipleUserIdsResponse(allUserIds)).asRight)
          }
      )

  val allRoutes: HttpRoutes[IO] = getAllUserIdsRoutes
}
