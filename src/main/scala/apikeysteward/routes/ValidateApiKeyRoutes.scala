package apikeysteward.routes

import apikeysteward.routes.definitions.Endpoints
import apikeysteward.routes.model.ValidateApiKeyResponse
import apikeysteward.services.ApiKeyService
import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ValidateApiKeyRoutes(apiKeyCreationService: ApiKeyService[String]) {

  private val validateApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        Endpoints.validateApiKeyEndpoint.serverLogic[IO] { request =>
          apiKeyCreationService.validateApiKey(request.apiKey).map { validationResult =>
            validationResult
              .fold(
                error => Left(ErrorInfo.forbiddenErrorDetail(Some(error))),
                apiKeyData => Right(StatusCode.Ok -> ValidateApiKeyResponse(apiKeyData))
              )
          }
        }
      )

  val allRoutes: HttpRoutes[IO] = validateApiKeyRoutes
}