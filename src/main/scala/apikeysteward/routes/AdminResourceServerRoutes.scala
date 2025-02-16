package apikeysteward.routes

import apikeysteward.model.errors.ResourceServerDbError
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.errors.ResourceServerDbError._
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminResourceServerEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.resourceserver._
import apikeysteward.services.ResourceServerService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminResourceServerRoutes(jwtAuthorizer: JwtAuthorizer, resourceServerService: ResourceServerService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createResourceServerRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminResourceServerEndpoints.createResourceServerEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, request) = input
          resourceServerService.createResourceServer(tenantId, request).map {

            case Right(newResourceServer) =>
              (StatusCode.Created, CreateResourceServerResponse(newResourceServer)).asRight

            case Left(_: ReferencedTenantDoesNotExistError) =>
              ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ReferencedTenantNotFound)).asLeft

            case Left(_: ResourceServerInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val updateResourceServerRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminResourceServerEndpoints.updateResourceServerEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, resourceServerId, request) = input
          resourceServerService.updateResourceServer(tenantId, resourceServerId, request).map {

            case Right(updatedResourceServer) =>
              (StatusCode.Ok, UpdateResourceServerResponse(updatedResourceServer)).asRight

            case Left(_: ResourceServerNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ResourceServerNotFound)).asLeft
          }
        }
    )

  private val deleteResourceServerRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminResourceServerEndpoints.deleteResourceServerEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, resourceServerId) = input
          resourceServerService.deleteResourceServer(tenantId, resourceServerId).map {

            case Right(deletedResourceServer) =>
              (StatusCode.Ok, DeleteResourceServerResponse(deletedResourceServer)).asRight

            case Left(_: ResourceServerNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ResourceServerNotFound)).asLeft

            case Left(_: ResourceServerDbError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val getSingleResourceServerRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminResourceServerEndpoints.getSingleResourceServerEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, resourceServerId) = input
          resourceServerService.getBy(tenantId, resourceServerId).map {
            case Some(resourceServer) => (StatusCode.Ok, GetSingleResourceServerResponse(resourceServer)).asRight
            case None =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ResourceServerNotFound)).asLeft
          }
        }
    )

  private val searchResourceServersRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminResourceServerEndpoints.searchResourceServersEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => tenantId =>
          resourceServerService.getAllForTenant(tenantId).map { resourceServers =>
            (StatusCode.Ok, GetMultipleResourceServersResponse(resourceServers)).asRight
          }
        }
    )

  val allRoutes: HttpRoutes[IO] =
    createResourceServerRoutes <+>
      updateResourceServerRoutes <+>
      deleteResourceServerRoutes <+>
      getSingleResourceServerRoutes <+>
      searchResourceServersRoutes

}
