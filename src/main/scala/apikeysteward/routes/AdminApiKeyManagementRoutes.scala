package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminApiKeyManagementEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.apikey.{CreateApiKeyAdminResponse, UpdateApiKeyAdminResponse}
import apikeysteward.routes.model.apikey.{DeleteApiKeyResponse, GetMultipleApiKeysResponse, GetSingleApiKeyResponse}
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminApiKeyManagementRoutes(jwtAuthorizer: JwtAuthorizer, managementService: ApiKeyManagementService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminApiKeyManagementEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
          .serverLogic { _ => adminRequest =>
            val (userId, request) = adminRequest.toUserRequest
            managementService.createApiKey(userId, request).map {

              case Right((newApiKey, apiKeyData)) =>
                (StatusCode.Created, CreateApiKeyAdminResponse(newApiKey.value, apiKeyData)).asRight

              case Left(validationError: ValidationError) =>
                ErrorInfo.badRequestErrorInfo(Some(validationError.message)).asLeft
              case Left(_: InsertionError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
          }
      )

  private val updateApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyManagementEndpoints.updateApiKeyEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (publicKeyId, updateApiKeyRequest) = input
          managementService.updateApiKey(publicKeyId, updateApiKeyRequest).map {

            case Right(apiKeyData) =>
              (StatusCode.Ok, UpdateApiKeyAdminResponse(apiKeyData)).asRight

            case Left(_: ApiKeyDbError.ApiKeyDataNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKey.ApiKeyNotFound)).asLeft
          }
        }
    )

  private val getAllApiKeysForUserRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminApiKeyManagementEndpoints.getAllApiKeysForUserEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => userId =>
            for {
              allApiKeyData <- managementService.getAllApiKeysFor(userId)

              result = (StatusCode.Ok -> GetMultipleApiKeysResponse(allApiKeyData)).asRight
            } yield result
          }
      )

  private val getApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminApiKeyManagementEndpoints.getSingleApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => publicKeyId =>
            managementService.getApiKey(publicKeyId).map {
              case Some(apiKeyData) => (StatusCode.Ok -> GetSingleApiKeyResponse(apiKeyData)).asRight
              case None =>
                ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKey.ApiKeyNotFound)).asLeft
            }
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminApiKeyManagementEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
          .serverLogic { _ => publicKeyId =>
            managementService.deleteApiKey(publicKeyId).map {
              case Right(deletedApiKeyData) =>
                (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

              case Left(_: ApiKeyDataNotFoundError) =>
                ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKey.ApiKeyNotFound)).asLeft
              case Left(_: ApiKeyDbError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+>
      updateApiKeyRoutes <+>
      getAllApiKeysForUserRoutes <+>
      getApiKeyRoutes <+>
      deleteApiKeyRoutes
}
