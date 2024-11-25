package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.model.RepositoryErrors.GenericError.UserDoesNotExistError
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{JwtAuthorizer, JwtOps}
import apikeysteward.routes.definitions.{ApiErrorMessages, ApiKeyManagementEndpoints}
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps, toTraverseOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyManagementRoutes(jwtOps: JwtOps, jwtAuthorizer: JwtAuthorizer, managementService: ApiKeyManagementService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => input =>
            val (_, request) = input
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              result <- userIdE.flatTraverse(managementService.createApiKey(_, request).map {
                case Right((newApiKey, apiKeyData)) =>
                  (StatusCode.Created -> CreateApiKeyResponse(newApiKey.value, apiKeyData)).asRight

                case Left(validationError: ValidationError) =>
                  ErrorInfo.badRequestErrorInfo(Some(validationError.message)).asLeft
                case Left(_: InsertionError) =>
                  ErrorInfo.internalServerErrorInfo().asLeft
              })

            } yield result
          }
      )

  private val getAllApiKeysRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.getAllApiKeysEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => tenantId =>
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              result <- userIdE.flatTraverse(
                managementService.getAllForUser(tenantId, _).map {

                  case Right(allApiKeyData) =>
                    (StatusCode.Ok -> GetMultipleApiKeysResponse(allApiKeyData)).asRight

                  case Left(_: UserDoesNotExistError) =>
                    ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.UserNotFound)).asLeft

                }
              )
            } yield result
          }
      )

  private val getSingleApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.getSingleApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => input =>
            val (_, publicKeyId) = input
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              result <- userIdE.flatTraverse(managementService.getApiKey(_, publicKeyId).map {
                case Some(apiKeyData) => (StatusCode.Ok -> GetSingleApiKeyResponse(apiKeyData)).asRight
                case None =>
                  ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.GetSingleApiKeyNotFound)).asLeft
              })

            } yield result
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => input =>
            val (_, publicKeyId) = input
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              result <- userIdE.flatTraverse(managementService.deleteApiKeyBelongingToUserWith(_, publicKeyId).map {
                case Right(deletedApiKeyData) =>
                  (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

                case Left(_: ApiKeyDataNotFoundError) =>
                  ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.DeleteApiKeyNotFound)).asLeft
                case Left(_: ApiKeyDbError) =>
                  ErrorInfo.internalServerErrorInfo().asLeft
              })

            } yield result
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysRoutes <+> getSingleApiKeyRoutes <+> deleteApiKeyRoutes

}
