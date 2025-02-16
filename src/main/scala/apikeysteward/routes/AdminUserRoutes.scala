package apikeysteward.routes

import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError._
import apikeysteward.model.errors.CommonError.UserDoesNotExistError
import apikeysteward.model.errors.UserDbError.UserInsertionError._
import apikeysteward.model.errors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.repositories.UserRepository.UserRepositoryError
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminUserEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.apikeytemplate.GetMultipleApiKeyTemplatesResponse
import apikeysteward.routes.model.admin.user._
import apikeysteward.routes.model.apikey.GetMultipleApiKeysResponse
import apikeysteward.services._
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminUserRoutes(
    jwtAuthorizer: JwtAuthorizer,
    userService: UserService,
    apiKeyTemplateService: ApiKeyTemplateService,
    apiKeyTemplateAssociationsService: ApiKeyTemplateAssociationsService,
    apiKeyService: ApiKeyManagementService
) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createUserRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.createUserEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, request) = input
          userService.createUser(tenantId, request).map {

            case Right(newUser) =>
              (StatusCode.Created, CreateUserResponse(newUser)).asRight

            case Left(_: UserInsertionError.ReferencedTenantDoesNotExistError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound)).asLeft

            case Left(_: UserAlreadyExistsForThisTenantError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.UserAlreadyExistsForThisTenant)).asLeft

            case Left(_: UserInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val deleteUserRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.deleteUserEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, userId) = input
          userService.deleteUser(tenantId, userId).map {

            case Right(deletedUser) =>
              (StatusCode.Ok, DeleteUserResponse(deletedUser)).asRight

            case Left(UserRepositoryError(_: UserNotFoundError)) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminUser.UserNotFound)).asLeft
          }
        }
    )

  private val getSingleUserRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.getSingleUserEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, userId) = input
          userService.getBy(tenantId, userId).map {
            case Some(user) => (StatusCode.Ok, GetSingleUserResponse(user)).asRight
            case None       => ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminUser.UserNotFound)).asLeft
          }
        }
    )

  private val getAllUsersForTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.getAllUsersForTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => tenantId =>
          userService.getAllForTenant(tenantId).map {

            case Right(allUsers) => (StatusCode.Ok -> GetMultipleUsersResponse(allUsers)).asRight

            case Left(_: UserInsertionError.ReferencedTenantDoesNotExistError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound)).asLeft
          }
        }
    )

  private val associateApiKeyTemplatesWithUserRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.associateApiKeyTemplatesWithUserEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, userId, request) = input
          apiKeyTemplateAssociationsService
            .associateApiKeyTemplatesWithUser(tenantId, userId, request.templateIds)
            .map {

              case Right(()) =>
                StatusCode.Created.asRight

              case Left(_: ApiKeyTemplatesUsersAlreadyExistsError) =>
                ErrorInfo
                  .badRequestErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ApiKeyTemplatesUsersAlreadyExists)
                  )
                  .asLeft

              case Left(_: ReferencedUserDoesNotExistError) =>
                ErrorInfo
                  .notFoundErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
                  )
                  .asLeft

              case Left(_: ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError) =>
                ErrorInfo
                  .badRequestErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedApiKeyTemplateNotFound)
                  )
                  .asLeft

              case Left(_: ApiKeyTemplatesUsersInsertionError) =>
                ErrorInfo.internalServerErrorInfo().asLeft
            }
        }
    )

  private val removeApiKeyTemplatesFromUserRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.removeApiKeyTemplatesFromUserEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, userId, request) = input
          apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(tenantId, userId, request.templateIds).map {

            case Right(()) =>
              StatusCode.Ok.asRight

            case Left(_: ReferencedUserDoesNotExistError) =>
              ErrorInfo
                .notFoundErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedApiKeyTemplateNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesUsersNotFoundError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ApiKeyTemplatesUsersNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesUsersDbError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val getAllApiKeyTemplatesForUserRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminUserEndpoints.getAllApiKeyTemplatesForUserEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, userId) = input
          apiKeyTemplateService.getAllForUser(tenantId, userId).map {

            case Right(allApiKeyTemplates) =>
              (StatusCode.Ok, GetMultipleApiKeyTemplatesResponse(allApiKeyTemplates)).asRight

            case Left(_: UserDoesNotExistError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
                )
                .asLeft
          }
        }
    )

  private val getAllApiKeysForUserRoutes: HttpRoutes[IO] =
    serverInterpreter
      .toRoutes(
        AdminUserEndpoints.getAllApiKeysForUserEndpoint
          .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
          .serverLogic { _ => input =>
            val (tenantId, userId) = input
            apiKeyService.getAllForUser(tenantId, userId).map {

              case Right(allApiKeyData) => (StatusCode.Ok -> GetMultipleApiKeysResponse(allApiKeyData)).asRight

              case Left(_: UserDoesNotExistError) =>
                ErrorInfo
                  .notFoundErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
                  )
                  .asLeft
            }
          }
      )

  val allRoutes: HttpRoutes[IO] =
    createUserRoutes <+>
      deleteUserRoutes <+>
      getSingleUserRoutes <+>
      getAllUsersForTenantRoutes <+>
      associateApiKeyTemplatesWithUserRoutes <+>
      removeApiKeyTemplatesFromUserRoutes <+>
      getAllApiKeyTemplatesForUserRoutes <+>
      getAllApiKeysForUserRoutes

}
