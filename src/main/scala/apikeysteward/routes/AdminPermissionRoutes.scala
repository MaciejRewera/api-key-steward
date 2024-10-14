package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.{
  PermissionAlreadyExistsForThisApplicationError,
  ReferencedApplicationDoesNotExistError
}
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminPermissionEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.permission._
import apikeysteward.services.PermissionService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminPermissionRoutes(jwtAuthorizer: JwtAuthorizer, permissionService: PermissionService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createPermissionRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminPermissionEndpoints.createPermissionEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { - => input =>
          val (applicationId, request) = input
          permissionService.createPermission(applicationId, request).map {

            case Right(newPermission) =>
              (StatusCode.Created, CreatePermissionResponse(newPermission)).asRight

            case Left(_: ReferencedApplicationDoesNotExistError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminPermission.ReferencedApplicationNotFound)).asLeft

            case Left(_: PermissionAlreadyExistsForThisApplicationError) =>
              ErrorInfo
                .badRequestErrorInfo(Some(ApiErrorMessages.AdminPermission.PermissionAlreadyExistsForThisApplication))
                .asLeft

            case Left(_: PermissionInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val deletePermissionRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminPermissionEndpoints.deletePermissionEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { - => input =>
          val (applicationId, permissionId) = input
          permissionService.deletePermission(applicationId, permissionId).map {

            case Right(deletedPermission) =>
              (StatusCode.Ok, DeletePermissionResponse(deletedPermission)).asRight

            case Left(_: PermissionNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminPermission.PermissionNotFound)).asLeft
          }
        }
    )

  private val getSinglePermissionRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminPermissionEndpoints.getSinglePermissionEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => input =>
          val (applicationId, permissionId) = input
          permissionService.getBy(applicationId, permissionId).map {
            case Some(permission) => (StatusCode.Ok, GetSinglePermissionResponse(permission)).asRight
            case None =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminPermission.PermissionNotFound)).asLeft
          }
        }
    )

  private val searchPermissionsRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminPermissionEndpoints.searchPermissionsEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => input =>
          val (applicationId, nameFragment) = input
          permissionService.getAllBy(applicationId)(nameFragment).map { permissions =>
            (StatusCode.Ok, GetMultiplePermissionsResponse(permissions)).asRight
          }
        }
    )

  val allRoutes: HttpRoutes[IO] =
    createPermissionRoutes <+>
      deletePermissionRoutes <+>
      getSinglePermissionRoutes <+>
      searchPermissionsRoutes

}
