package apikeysteward.routes

import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.ApiKeyDataNotFound
import apikeysteward.routes.auth.JwtValidator
import apikeysteward.routes.auth.model.{JsonWebToken, JwtPermissions}
import apikeysteward.routes.definitions.{ErrorMessages, ManagementEndpoints}
import apikeysteward.routes.model.{CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ManagementRoutes(jwtValidator: JwtValidator, adminService: AdminService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ManagementEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => request =>
            withUserId(jwt) { userId =>
              adminService.createApiKey(userId, request).map { case (newApiKey, apiKeyData) =>
                (
                  StatusCode.Created,
                  CreateApiKeyResponse(newApiKey.value, apiKeyData)
                ).asRight
              }
            }
          }
      )

  private val getAllApiKeysRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ManagementEndpoints.getAllApiKeysEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => _ =>
            withUserId(jwt) { userId =>
              adminService.getAllApiKeysFor(userId).map { allApiKeyData =>
                if (allApiKeyData.isEmpty) {
                  val errorMsg = ErrorMessages.Management.GetAllApiKeysNotFound
                  ErrorInfo.notFoundErrorInfo(Some(errorMsg)).asLeft
                } else
                  (StatusCode.Ok -> allApiKeyData).asRight
              }
            }
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ManagementEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => publicKeyId =>
            withUserId(jwt) { userId =>
              adminService.deleteApiKey(userId, publicKeyId).map {
                case Right(deletedApiKeyData) =>
                  (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

                case Left(_: ApiKeyDataNotFound) =>
                  ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.Management.DeleteApiKeyNotFound)).asLeft
                case Left(_: ApiKeyDeletionError) =>
                  ErrorInfo.internalServerErrorInfo().asLeft
              }
            }
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysRoutes <+> deleteApiKeyRoutes

  private def withUserId[A](
      jwt: JsonWebToken
  )(f: String => IO[Either[ErrorInfo, (StatusCode, A)]]): IO[Either[ErrorInfo, (StatusCode, A)]] =
    jwt.jwtClaim.subject match {
      case Some(userId) => f(userId)
      case None =>
        IO.pure(ErrorInfo.badRequestErrorInfo(Some("'sub' field in provided JWT cannot be empty.")).asLeft)
    }
}
