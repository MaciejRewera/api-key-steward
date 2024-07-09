package apikeysteward.routes

import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.ApiKeyDataNotFound
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.{CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.ManagementService
import apikeysteward.services.ManagementService.ApiKeyCreationError.{InsertionError, ValidationError}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminRoutes(jwtAuthorizer: JwtAuthorizer, managementService: ManagementService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
          .serverLogic { _ => input =>
            val (request, userId) = input
            managementService.createApiKey(userId, request).map {

              case Right((newApiKey, apiKeyData)) =>
                (StatusCode.Created, CreateApiKeyResponse(newApiKey.value, apiKeyData)).asRight

              case Left(validationError: ValidationError) =>
                ErrorInfo.badRequestErrorInfo(Some(validationError.message)).asLeft
              case Left(_: InsertionError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
          }
      )

  private val getAllApiKeysForUserRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.getAllApiKeysForUserEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => userId =>
            for {
              allApiKeyData <- managementService.getAllApiKeysFor(userId)

              result = (StatusCode.Ok -> allApiKeyData).asRight
            } yield result
          }
      )

  private val getAllUserIdsRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.getAllUserIdsEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => _ =>
            managementService.getAllUserIds
              .map(allUserIds => (StatusCode.Ok -> allUserIds).asRight)
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
          .serverLogic { _ => input =>
            val (userId, publicKeyId) = input
            managementService.deleteApiKey(userId, publicKeyId).map {
              case Right(deletedApiKeyData) =>
                (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

              case Left(_: ApiKeyDataNotFound) =>
                ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Admin.DeleteApiKeyNotFound)).asLeft
              case Left(_: ApiKeyDeletionError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysForUserRoutes <+> getAllUserIdsRoutes <+> deleteApiKeyRoutes
}
