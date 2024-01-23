package apikeysteward.routes

import apikeysteward.routes.model.CreateApiKeyResponse
import cats.effect.IO
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyRoutes {

  private val createApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        Endpoints.createApiKeyEndpoint.serverLogic[IO] { request =>
          (
            StatusCode.Created,
            CreateApiKeyResponse(request.userId, request.apiKeyName, "at-some-point-this-will-be-a- valid-api-key")
          ).asRight[Unit].pure[IO]
        }
      )

  val allRoutes: HttpRoutes[IO] = createApiKeyRoutes
}
