package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError._
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminTenantEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.tenant._
import apikeysteward.services.TenantService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminTenantRoutes(jwtAuthorizer: JwtAuthorizer, tenantService: TenantService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.createTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => request =>
          tenantService.createTenant(request).map {

            case Right(newTenant) =>
              (StatusCode.Created, CreateTenantResponse(newTenant)).asRight

            case Left(_: TenantInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val updateTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.updateTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, request) = input
          tenantService.updateTenant(tenantId, request).map {

            case Right(updatedTenant) =>
              (StatusCode.Ok, UpdateTenantResponse(updatedTenant)).asRight

            case Left(_: TenantNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)).asLeft
          }
        }
    )

  private val reactivateTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.reactivateTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => tenantId =>
          tenantService.reactivateTenant(tenantId).map {

            case Right(reactivatedTenant) =>
              (StatusCode.Ok, ReactivateTenantResponse(reactivatedTenant)).asRight

            case Left(_: TenantNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)).asLeft
          }
        }
    )

  private val deactivateTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.deactivateTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => tenantId =>
          tenantService.deactivateTenant(tenantId).map {

            case Right(deactivatedTenant) =>
              (StatusCode.Ok, DeactivateTenantResponse(deactivatedTenant)).asRight

            case Left(_: TenantNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)).asLeft
          }
        }
    )

  private val deleteTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.deleteTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => tenantId =>
          tenantService.deleteTenant(tenantId).map {

            case Right(deletedTenant) =>
              (StatusCode.Ok, DeleteTenantResponse(deletedTenant)).asRight

            case Left(_: TenantNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)).asLeft

            case Left(_: TenantIsNotDeactivatedError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantIsNotDeactivated(tenantId))).asLeft

            case Left(_: CannotDeleteDependencyError) =>
              ErrorInfo
                .badRequestErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantDependencyCannotBeDeleted(tenantId)))
                .asLeft

            case Left(_: TenantDbError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val getSingleTenantRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.getSingleTenantEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => tenantId =>
          tenantService.getBy(tenantId).map {
            case Some(tenant) => (StatusCode.Ok, GetSingleTenantResponse(tenant)).asRight
            case None =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)).asLeft
          }
        }
    )

  private val getAllTenantsRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminTenantEndpoints.getAllTenantsEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => _ =>
          tenantService.getAll.map(allTenants => (StatusCode.Ok, GetMultipleTenantsResponse(allTenants)).asRight)
        }
    )

  val allRoutes: HttpRoutes[IO] =
    createTenantRoutes <+>
      updateTenantRoutes <+>
      reactivateTenantRoutes <+>
      deactivateTenantRoutes <+>
      deleteTenantRoutes <+>
      getSingleTenantRoutes <+>
      getAllTenantsRoutes

}
