package apikeysteward.routes

import apikeysteward.routes.model.CreateApiKeyResponse
import apikeysteward.services.ApiKeyCreationService
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyRoutes(apiKeyCreationService: ApiKeyCreationService[String]) {

  private val createApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        Endpoints.createApiKeyEndpoint.serverLogic[IO] { request =>
          apiKeyCreationService.createApiKey(request).map { newApiKey =>
            (
              StatusCode.Created,
              CreateApiKeyResponse(request.userId, request.apiKeyName, newApiKey)
            ).asRight[Unit]
          }
        }
      )

  val allRoutes: HttpRoutes[IO] = createApiKeyRoutes
}
