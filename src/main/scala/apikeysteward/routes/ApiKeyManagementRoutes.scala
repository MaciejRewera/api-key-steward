package apikeysteward.routes

import apikeysteward.model.errors.ApiKeyDbError
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.model.errors.CommonError.UserDoesNotExistError
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{JwtAuthorizer, JwtOps}
import apikeysteward.routes.definitions.{ApiErrorMessages, ApiKeyManagementEndpoints}
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ApiKeyManagementRoutes(
    jwtOps: JwtOps,
    jwtAuthorizer: JwtAuthorizer,
    activeTenantVerifier: ActiveTenantVerifier,
    managementService: ApiKeyManagementService
) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => input =>
            val (tenantId, request) = input

            (for {
              _      <- activeTenantVerifier.verifyTenantIsActive(tenantId)
              userId <- EitherT.fromEither[IO](jwtOps.extractUserId(jwt))

              result <- EitherT {
                managementService.createApiKey(tenantId, userId, request).map {
                  case Right((newApiKey, apiKeyData)) =>
                    (StatusCode.Created -> CreateApiKeyResponse(newApiKey.value, apiKeyData)).asRight

                  case Left(validationError: ValidationError) =>
                    ErrorInfo.badRequestErrorInfo(Some(validationError.message)).asLeft
                  case Left(_: InsertionError) =>
                    ErrorInfo.internalServerErrorInfo().asLeft
                }
              }

            } yield result).value
          }
      )

  private val getAllApiKeysRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.getAllApiKeysEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => tenantId =>
            (for {
              _      <- activeTenantVerifier.verifyTenantIsActive(tenantId)
              userId <- EitherT.fromEither[IO](jwtOps.extractUserId(jwt))

              result <- EitherT {
                managementService.getAllForUser(tenantId, userId).map {

                  case Right(allApiKeyData) =>
                    (StatusCode.Ok -> GetMultipleApiKeysResponse(allApiKeyData)).asRight

                  case Left(_: UserDoesNotExistError) =>
                    ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.UserNotFound)).asLeft
                }
              }
            } yield result).value
          }
      )

  private val getSingleApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.getSingleApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => input =>
            val (tenantId, publicKeyId) = input

            (for {
              _      <- activeTenantVerifier.verifyTenantIsActive(tenantId)
              userId <- EitherT.fromEither[IO](jwtOps.extractUserId(jwt))

              result <- EitherT {
                managementService.getApiKey(tenantId, userId, publicKeyId).map {
                  case Some(apiKeyData) => (StatusCode.Ok -> GetSingleApiKeyResponse(apiKeyData)).asRight
                  case None =>
                    ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.GetSingleApiKeyNotFound)).asLeft
                }
              }
            } yield result).value
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ApiKeyManagementEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => input =>
            val (tenantId, publicKeyId) = input

            (for {
              _      <- activeTenantVerifier.verifyTenantIsActive(tenantId)
              userId <- EitherT.fromEither[IO](jwtOps.extractUserId(jwt))

              result <- EitherT {
                managementService.deleteApiKeyBelongingToUserWith(tenantId, userId, publicKeyId).map {
                  case Right(deletedApiKeyData) =>
                    (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

                  case Left(_: ApiKeyDataNotFoundError) =>
                    ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.DeleteApiKeyNotFound)).asLeft
                  case Left(_: ApiKeyDbError) =>
                    ErrorInfo.internalServerErrorInfo().asLeft
                }
              }
            } yield result).value
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysRoutes <+> getSingleApiKeyRoutes <+> deleteApiKeyRoutes

}
