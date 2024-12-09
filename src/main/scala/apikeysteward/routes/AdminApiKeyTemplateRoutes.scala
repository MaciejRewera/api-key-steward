package apikeysteward.routes

import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.errors.ApiKeyTemplateDbError._
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError.{
  ApiKeyTemplatesPermissionsInsertionError,
  ApiKeyTemplatesPermissionsNotFoundError
}
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.{
  ApiKeyTemplatesUsersAlreadyExistsError,
  ReferencedUserDoesNotExistError
}
import apikeysteward.model.errors.GenericError.ApiKeyTemplateDoesNotExistError
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminApiKeyTemplateEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.apikeytemplate._
import apikeysteward.routes.model.admin.permission.GetMultiplePermissionsResponse
import apikeysteward.routes.model.admin.user.GetMultipleUsersResponse
import apikeysteward.services.{ApiKeyTemplateAssociationsService, ApiKeyTemplateService, PermissionService, UserService}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminApiKeyTemplateRoutes(
    jwtAuthorizer: JwtAuthorizer,
    apiKeyTemplateService: ApiKeyTemplateService,
    permissionService: PermissionService,
    userService: UserService,
    apiKeyTemplateAssociationsService: ApiKeyTemplateAssociationsService
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
          val (tenantId, apiKeyTemplateId, request) = input
          apiKeyTemplateService.updateApiKeyTemplate(tenantId, apiKeyTemplateId, request).map {

            case Right(updatedApiKeyTemplate) =>
              (StatusCode.Ok, UpdateApiKeyTemplateResponse(updatedApiKeyTemplate)).asRight

            case Left(_: ApiKeyTemplateNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound)).asLeft
          }
        }
    )

  private val deleteApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.deleteResourceServerEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, apiKeyTemplateId) = input
          apiKeyTemplateService.deleteApiKeyTemplate(tenantId, apiKeyTemplateId).map {

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
        .serverLogic { _ => input =>
          val (tenantId, apiKeyTemplateId) = input
          apiKeyTemplateService.getBy(tenantId, apiKeyTemplateId).map {
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
          val (tenantId, apiKeyTemplateId, request) = input
          apiKeyTemplateAssociationsService
            .associatePermissionsWithApiKeyTemplate(tenantId, apiKeyTemplateId, request.permissionIds)
            .map {

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

              case Left(_: ApiKeyTemplatesPermissionsInsertionError.ReferencedApiKeyTemplateDoesNotExistError) =>
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
          val (tenantId, apiKeyTemplateId, request) = input
          apiKeyTemplateAssociationsService
            .removePermissionsFromApiKeyTemplate(tenantId, apiKeyTemplateId, request.permissionIds)
            .map {

              case Right(()) =>
                StatusCode.Ok.asRight

              case Left(_: ReferencedPermissionDoesNotExistError) =>
                ErrorInfo
                  .badRequestErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedPermissionNotFound)
                  )
                  .asLeft

              case Left(_: ApiKeyTemplatesPermissionsInsertionError.ReferencedApiKeyTemplateDoesNotExistError) =>
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
        .serverLogic { _ => input =>
          val (tenantId, apiKeyTemplateId) = input
          permissionService.getAllForTemplate(tenantId, apiKeyTemplateId).map {

            case Right(permissions) =>
              (StatusCode.Ok, GetMultiplePermissionsResponse(permissions)).asRight

            case Left(_: ApiKeyTemplateDoesNotExistError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.General.ApiKeyTemplateNotFound)).asLeft
          }
        }
    )

  private val associateUsersWithApiKeyTemplateRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApiKeyTemplateEndpoints.associateUsersWithApiKeyTemplateEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, apiKeyTemplateId, request) = input
          apiKeyTemplateAssociationsService
            .associateUsersWithApiKeyTemplate(tenantId, apiKeyTemplateId, request.userIds)
            .map {

              case Right(()) =>
                StatusCode.Created.asRight

              case Left(_: ApiKeyTemplatesUsersAlreadyExistsError) =>
                ErrorInfo
                  .badRequestErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleTemplate.ApiKeyTemplatesUsersAlreadyExists)
                  )
                  .asLeft

              case Left(_: ReferencedUserDoesNotExistError) =>
                ErrorInfo
                  .badRequestErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleTemplate.ReferencedUserNotFound)
                  )
                  .asLeft

              case Left(_: ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError) =>
                ErrorInfo
                  .notFoundErrorInfo(
                    Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleTemplate.ReferencedApiKeyTemplateNotFound)
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
        .serverLogic { _ => input =>
          val (tenantId, apiKeyTemplateId) = input
          userService.getAllForTemplate(tenantId, apiKeyTemplateId).map {

            case Right(allUsers) => (StatusCode.Ok, GetMultipleUsersResponse(allUsers)).asRight

            case Left(_: ApiKeyTemplateDoesNotExistError) =>
              ErrorInfo
                .notFoundErrorInfo(
                  Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleTemplate.ReferencedApiKeyTemplateNotFound)
                )
                .asLeft
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
