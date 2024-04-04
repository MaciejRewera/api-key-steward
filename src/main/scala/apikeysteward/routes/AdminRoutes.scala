package apikeysteward.routes

import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.ApiKeyDataNotFound
import apikeysteward.routes.definitions.AdminEndpoints.ErrorMessages
import apikeysteward.routes.definitions.{AdminEndpoints, ServerConfiguration}
import apikeysteward.routes.model.admin.{CreateApiKeyAdminResponse, DeleteApiKeyAdminResponse}
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminRoutes(adminService: AdminService[String]) {

  private val createApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        AdminEndpoints.createApiKeyEndpoint.serverLogic[IO] { case (userId, request) =>
          adminService.createApiKey(userId, request).map { case (newApiKey, apiKeyData) =>
            (
              StatusCode.Created,
              CreateApiKeyAdminResponse(newApiKey, apiKeyData)
            ).asRight[Unit]
          }
        }
      )

  private val getAllApiKeysForUserRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        AdminEndpoints.getAllApiKeysForUserEndpoint.serverLogic[IO] { userId =>
          adminService.getAllApiKeysFor(userId).map { allApiKeyData =>
            if (allApiKeyData.isEmpty) {
              val errorMsg = ErrorMessages.GetAllApiKeysForUserNotFound
              Left(ErrorInfo.notFoundErrorInfo(Some(errorMsg)))
            } else
              Right(StatusCode.Ok -> allApiKeyData)
          }
        }
      )

  private val getAllUserIdsRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        AdminEndpoints.getAllUserIdsEndpoint.serverLogic[IO] { _ =>
          adminService.getAllUserIds
            .map(allUserIds => (StatusCode.Ok -> allUserIds).asRight[Unit])
        }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter(ServerConfiguration.options)
      .toRoutes(
        AdminEndpoints.deleteApiKeyEndpoint.serverLogic[IO] { case (userId, publicKeyId) =>
          adminService.deleteApiKey(userId, publicKeyId).map {
            case Right(deletedApiKeyData) =>
              (StatusCode.Ok -> DeleteApiKeyAdminResponse(deletedApiKeyData)).asRight

            case Left(_: ApiKeyDataNotFound) =>
              ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.DeleteApiKeyNotFound)).asLeft
            case Left(_: ApiKeyDeletionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysForUserRoutes <+> getAllUserIdsRoutes <+> deleteApiKeyRoutes
}
