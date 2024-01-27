package apikeysteward.routes

import cats.implicits._
import apikeysteward.routes.model.{ApiKeyData, CreateApiKeyResponse, ValidateApiKeyResponse}
import apikeysteward.services.ApiKeyService
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyRoutes(apiKeyCreationService: ApiKeyService[String]) {

  private val createApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        Endpoints.createApiKeyEndpoint.serverLogic[IO] { request =>
          apiKeyCreationService.createApiKey(ApiKeyData.from(request)).map { newApiKey =>
            (
              StatusCode.Created,
              CreateApiKeyResponse(newApiKey, ApiKeyData(request.userId, request.apiKeyName))
            ).asRight[Unit]
          }
        }
      )

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

  val allRoutes: HttpRoutes[IO] = createApiKeyRoutes <+> validateApiKeyRoutes
}
