package apikeysteward.routes

import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.ApiKeyDataNotFound
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{JwtAuthorizer, JwtOps}
import apikeysteward.routes.definitions.{ApiErrorMessages, ManagementEndpoints}
import apikeysteward.routes.model.{CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.ManagementService
import apikeysteward.services.ManagementService.ApiKeyCreationError.{InsertionError, ValidationError}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps, toTraverseOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ManagementRoutes(jwtOps: JwtOps, jwtAuthorizer: JwtAuthorizer, managementService: ManagementService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ManagementEndpoints.createApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => request =>
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
        ManagementEndpoints.getAllApiKeysEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => _ =>
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              allApiKeyDataE <- userIdE.traverse(managementService.getAllApiKeysFor)

              result = allApiKeyDataE.flatMap(allApiKeyData => (StatusCode.Ok -> allApiKeyData).asRight)
            } yield result
          }
      )

  private val getSingleApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ManagementEndpoints.getSingleApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadApiKey))(_))
          .serverLogic { jwt => publicKeyId =>
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              result <- userIdE.flatTraverse(managementService.getApiKey(_, publicKeyId).map {
                case Some(apiKeyData) => (StatusCode.Ok -> apiKeyData).asRight
                case None =>
                  ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.GetSingleApiKeyNotFound)).asLeft
              })

            } yield result
          }
      )

  private val deleteApiKeyRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        ManagementEndpoints.deleteApiKeyEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteApiKey))(_))
          .serverLogic { jwt => publicKeyId =>
            for {
              userIdE <- IO(jwtOps.extractUserId(jwt))
              result <- userIdE.flatTraverse(managementService.deleteApiKey(_, publicKeyId).map {
                case Right(deletedApiKeyData) =>
                  (StatusCode.Ok -> DeleteApiKeyResponse(deletedApiKeyData)).asRight

                case Left(_: ApiKeyDataNotFound) =>
                  ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.DeleteApiKeyNotFound)).asLeft
                case Left(_: ApiKeyDeletionError) =>
                  ErrorInfo.internalServerErrorInfo().asLeft
              })

            } yield result
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyRoutes <+> getAllApiKeysRoutes <+> getSingleApiKeyRoutes <+> deleteApiKeyRoutes

}
