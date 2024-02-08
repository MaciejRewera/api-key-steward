package apikeysteward.routes

import apikeysteward.routes.definitions.AdminEndpoints
import apikeysteward.routes.model.admin.CreateApiKeyAdminResponse
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminRoutes(adminService: AdminService[String]) {

  private val getAllApiKeysForUserRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        AdminEndpoints.getAllApiKeysForUserEndpoint.serverLogic[IO] { userId =>
          adminService.getAllApiKeysFor(userId).map { allApiKeyData =>
            if (allApiKeyData.isEmpty) {
              val errorMsg = "No API Key found for provided userId."
              Left(ErrorInfo.notFoundErrorDetail(Some(errorMsg)))
            } else
              Right(StatusCode.Ok -> allApiKeyData)
          }
        }
      )

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

  val allRoutes: HttpRoutes[IO] = createApiKeyRoutes <+> getAllApiKeysForUserRoutes
}
