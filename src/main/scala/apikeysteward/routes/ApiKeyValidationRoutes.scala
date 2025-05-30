package apikeysteward.routes

import apikeysteward.model.ApiKey
import apikeysteward.routes.definitions.{ApiErrorMessages, ApiKeyValidationEndpoints}
import apikeysteward.routes.model.apikey.ValidateApiKeyResponse
import apikeysteward.services.ApiKeyValidationService
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.data.EitherT
import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyValidationRoutes(
    activeTenantVerifier: ActiveTenantVerifier,
    apiKeyValidationService: ApiKeyValidationService
) {

  private val validateApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        ApiKeyValidationEndpoints.validateApiKeyEndpoint.serverLogic[IO] { input =>
          val (tenantId, request) = input

          (for {
            _ <- activeTenantVerifier.verifyTenantIsActive(tenantId)

            result <- EitherT {
              apiKeyValidationService.validateApiKey(tenantId, ApiKey(request.apiKey)).map {

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
          } yield result).value
        }
      )

  val allRoutes: HttpRoutes[IO] = validateApiKeyRoutes
}
