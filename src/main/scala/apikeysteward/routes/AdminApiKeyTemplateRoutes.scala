package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.{
  ApiKeyTemplatesPermissionsInsertionError,
  ApiKeyTemplatesPermissionsNotFoundError
}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.{
  ApiKeyTemplatesUsersAlreadyExistsError,
  ReferencedUserDoesNotExistError
}
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminApiKeyTemplateEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.apikeytemplate._
import apikeysteward.routes.model.admin.permission.GetMultiplePermissionsResponse
import apikeysteward.routes.model.admin.user.GetMultipleUsersResponse
import apikeysteward.services.{ApiKeyTemplateService, PermissionService, UserService}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminApiKeyTemplateRoutes(
    jwtAuthorizer: JwtAuthorizer,
    apiKeyTemplateService: ApiKeyTemplateService,
    permissionService: PermissionService,
    userService: UserService
) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.createApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, request) = input
          apiKeyTemplateService.createApiKeyTemplate(tenantId, request).map {

            case Right(newApiKeyTemplate) =>
              (StatusCode.Created, CreateApiKeyTemplateResponse(newApiKeyTemplate)).asRight

            case Left(_: ReferencedTenantDoesNotExistError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ReferencedTenantNotFound)).asLeft

            case Left(_: ApiKeyTemplateInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val updateApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.updateApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (apiKeyTemplateId, request) = input
          apiKeyTemplateService.updateApiKeyTemplate(apiKeyTemplateId, request).map {

            case Right(updatedApiKeyTemplate) =>
              (StatusCode.Ok, UpdateApiKeyTemplateResponse(updatedApiKeyTemplate)).asRight

            case Left(_: ApiKeyTemplateNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound)).asLeft
          }
        }
    )

  private val deleteApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.deleteApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => apiKeyTemplateId =>
          apiKeyTemplateService.deleteApiKeyTemplate(apiKeyTemplateId).map {

            case Right(deletedApiKeyTemplate) =>
              (StatusCode.Ok, DeleteApiKeyTemplateResponse(deletedApiKeyTemplate)).asRight

            case Left(_: ApiKeyTemplateNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound)).asLeft
          }
        }
    )

  private val getSingleApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.getSingleApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => apiKeyTemplateId =>
          apiKeyTemplateService.getBy(apiKeyTemplateId).map {
            case Some(apiKeyTemplate) => (StatusCode.Ok, GetSingleApiKeyTemplateResponse(apiKeyTemplate)).asRight
            case None =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound)).asLeft
          }
        }
    )

  private val searchApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.searchApiKeyTemplatesEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => tenantId =>
          apiKeyTemplateService.getAllForTenant(tenantId).map { apiKeyTemplates =>
            (StatusCode.Ok, GetMultipleApiKeyTemplatesResponse(apiKeyTemplates)).asRight
          }
        }
    )

  private val associatePermissionsWithApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.associatePermissionsWithApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (apiKeyTemplateId, request) = input
          apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(apiKeyTemplateId, request.permissionIds).map {

            case Right(()) =>
              StatusCode.Created.asRight

            case Left(_: ApiKeyTemplatesPermissionsAlreadyExistsError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ApiKeyTemplatesPermissionsAlreadyExists)
                )
                .asLeft

            case Left(_: ReferencedPermissionDoesNotExistError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedPermissionNotFound)
                )
                .asLeft

            case Left(_: ReferencedApiKeyTemplateDoesNotExistError) =>
              ErrorInfo
                .notFoundErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedApiKeyTemplateNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesPermissionsInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val removePermissionsFromApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.removePermissionsFromApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (apiKeyTemplateId, request) = input
          apiKeyTemplateService.removePermissionsFromApiKeyTemplate(apiKeyTemplateId, request.permissionIds).map {

            case Right(()) =>
              StatusCode.Ok.asRight

            case Left(_: ReferencedPermissionDoesNotExistError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedPermissionNotFound)
                )
                .asLeft

            case Left(_: ReferencedApiKeyTemplateDoesNotExistError) =>
              ErrorInfo
                .notFoundErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedApiKeyTemplateNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesPermissionsNotFoundError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ApiKeyTemplatesPermissionsNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesPermissionsDbError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val getAllPermissionsForTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.getAllPermissionsForTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => apiKeyTemplateId =>
          permissionService.getAllFor(apiKeyTemplateId).map { permissions =>
            (StatusCode.Ok, GetMultiplePermissionsResponse(permissions)).asRight
          }
        }
    )

  private val associateUsersWithApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.associateUsersWithApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (apiKeyTemplateId, request) = input
          apiKeyTemplateService.associateUsersWithApiKeyTemplate(apiKeyTemplateId, request.userIds).map {

            case Right(()) =>
              StatusCode.Created.asRight

            case Left(_: ApiKeyTemplatesUsersAlreadyExistsError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ApiKeyTemplatesUsersAlreadyExists)
                )
                .asLeft

            case Left(_: ReferencedUserDoesNotExistError) =>
              ErrorInfo
                .badRequestErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ReferencedUserNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError) =>
              ErrorInfo
                .notFoundErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ReferencedApiKeyTemplateNotFound)
                )
                .asLeft

            case Left(_: ApiKeyTemplatesUsersInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val getAllUsersForTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.getAllUsersForTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => apiKeyTemplateId =>
          userService.getAllFor(apiKeyTemplateId).map { users =>
            (StatusCode.Ok, GetMultipleUsersResponse(users)).asRight
          }
        }
    )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyTemplateRoutes <+>
      updateApiKeyTemplateRoutes <+>
      deleteApiKeyTemplateRoutes <+>
      getSingleApiKeyTemplateRoutes <+>
      searchApiKeyTemplateRoutes <+>
      associatePermissionsWithApiKeyTemplateRoutes <+>
      removePermissionsFromApiKeyTemplateRoutes <+>
      getAllPermissionsForTemplateRoutes <+>
      associateUsersWithApiKeyTemplateRoutes <+>
      getAllUsersForTemplateRoutes

}
