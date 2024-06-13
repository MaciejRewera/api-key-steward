package apikeysteward.routes

import apikeysteward.model.ApiKey
import apikeysteward.routes.definitions.{ApiErrorMessages, ValidateApiKeyEndpoints}
import apikeysteward.routes.model.ValidateApiKeyResponse
import apikeysteward.services.ApiKeyValidationService
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyValidationRoutes(apiKeyValidationService: ApiKeyValidationService) {

  private val validateApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        ValidateApiKeyEndpoints.validateApiKeyEndpoint.serverLogic[IO] { request =>
          apiKeyValidationService.validateApiKey(ApiKey(request.apiKey)).map {

            case Right(apiKeyData) => Right(StatusCode.Ok -> ValidateApiKeyResponse(apiKeyData))

            case Left(ApiKeyIncorrectError) =>
              Left(ErrorInfo.forbiddenErrorInfo(Some(ApiErrorMessages.ValidateApiKey.ValidateApiKeyIncorrect)))

            case Left(expiryError: ApiKeyExpiredError) =>
              Left(
                ErrorInfo.forbiddenErrorInfo(
                  Some(ApiErrorMessages.ValidateApiKey.validateApiKeyExpired(expiryError.expiredSince))
                )
              )
          }
        }
      )

  val allRoutes: HttpRoutes[IO] = validateApiKeyRoutes
}
