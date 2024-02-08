package apikeysteward.routes

import apikeysteward.routes.definitions.AdminEndpoints
import apikeysteward.routes.model.admin.CreateApiKeyAdminResponse
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminRoutes(adminService: AdminService[String]) {

  private val createApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        AdminEndpoints.createApiKeyEndpoint.serverLogic[IO] { request =>
          adminService.createApiKey(request).map { case (newApiKey, apiKeyData) =>
            (
              StatusCode.Created,
              CreateApiKeyAdminResponse(newApiKey, apiKeyData)
            ).asRight[Unit]
          }
        }
      )

  val allRoutes: HttpRoutes[IO] = createApiKeyRoutes
}
