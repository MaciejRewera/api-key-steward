package apikeysteward.routes

import apikeysteward.repositories.db.DbCommons.{ApiKeyDeletionError, ApiKeyUpdateError}
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.UpdateApiKeyResponse
import apikeysteward.routes.model.{CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.ManagementService
import apikeysteward.services.ManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
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

  private val updateApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminEndpoints.updateApiKeyEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (updateApiKeyRequest, userId, publicKeyId) = input
          managementService.updateApiKey(userId, publicKeyId, updateApiKeyRequest).map {

            case Right(apiKeyData) =>
              (StatusCode.Ok, UpdateApiKeyResponse(apiKeyData)).asRight

            case Left(_: ApiKeyUpdateError.ApiKeyDataNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Admin.UpdateApiKeyNotFound)).asLeft
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

  private val getApiKeyForUserRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminEndpoints.getSingleApiKeyForUserEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => input =>
            val (userId, publicKeyId) = input
            managementService.getApiKey(userId, publicKeyId).map {
              case Some(apiKeyData) => (StatusCode.Ok -> apiKeyData).asRight
              case None =>
                ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Admin.GetSingleApiKeyNotFound)).asLeft
            }
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

              case Left(_: ApiKeyDeletionError.ApiKeyDataNotFoundError) =>
                ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Admin.DeleteApiKeyNotFound)).asLeft
              case Left(_: ApiKeyDeletionError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+>
      updateApiKeyRoutes <+>
      getAllApiKeysForUserRoutes <+>
      getApiKeyForUserRoutes <+>
      getAllUserIdsRoutes <+>
      deleteApiKeyRoutes
}
