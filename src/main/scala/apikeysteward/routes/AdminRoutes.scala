package apikeysteward.routes

import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.ApiKeyDataNotFound
import apikeysteward.routes.auth.JwtValidator
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminEndpoints, ErrorMessages}
import apikeysteward.routes.model.{CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminRoutes(jwtValidator: JwtValidator, adminService: AdminService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
          .serverLogic { _ => input =>
            val (request, userId) = input
            adminService.createApiKey(userId, request).map { case (newApiKey, apiKeyData) =>
              (
                StatusCode.Created,
                CreateApiKeyResponse(newApiKey, apiKeyData)
              ).asRight
            }
          }
      )

  private val getAllApiKeysForUserRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.getAllApiKeysForUserEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => userId =>
            adminService.getAllApiKeysFor(userId).map { allApiKeyData =>
              if (allApiKeyData.isEmpty) {
                val errorMsg = ErrorMessages.Admin.GetAllApiKeysForUserNotFound
                ErrorInfo.notFoundErrorInfo(Some(errorMsg)).asLeft
              } else
                (StatusCode.Ok -> allApiKeyData).asRight
            }
          }
      )

  private val getAllUserIdsRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.getAllUserIdsEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => _ =>
            adminService.getAllUserIds
              .map(allUserIds => (StatusCode.Ok -> allUserIds).asRight)
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtValidator.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
          .serverLogic { _ => input =>
            val (userId, publicKeyId) = input
            adminService.deleteApiKey(userId, publicKeyId).map {
              case Right(deletedApiKeyData) =>
                (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

              case Left(_: ApiKeyDataNotFound) =>
                ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.Admin.DeleteApiKeyNotFound)).asLeft
              case Left(_: ApiKeyDeletionError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysForUserRoutes <+> getAllUserIdsRoutes <+> deleteApiKeyRoutes
}
