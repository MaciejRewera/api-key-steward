package apikeysteward.routes

import apikeysteward.model.ApiKey
import apikeysteward.routes.definitions.ValidateApiKeyEndpoints
import apikeysteward.routes.model.ValidateApiKeyResponse
import apikeysteward.services.ApiKeyService
import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ValidateApiKeyRoutes(apiKeyCreationService: ApiKeyService) {

  private val validateApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        ValidateApiKeyEndpoints.validateApiKeyEndpoint.serverLogic[IO] { request =>
          apiKeyCreationService.validateApiKey(ApiKey(request.apiKey)).map { validationResult =>
            validationResult
              .fold(
                error => Left(ErrorInfo.forbiddenErrorInfo(Some(error))),
                apiKeyData => Right(StatusCode.Ok -> ValidateApiKeyResponse(apiKeyData))
              )
          }
        }
      )

  val allRoutes: HttpRoutes[IO] = validateApiKeyRoutes
}
