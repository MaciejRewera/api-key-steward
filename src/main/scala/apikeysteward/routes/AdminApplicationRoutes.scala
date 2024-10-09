package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError._
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminApplicationEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.application._
import apikeysteward.services.ApplicationService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminApplicationRoutes(jwtAuthorizer: JwtAuthorizer, applicationService: ApplicationService) {

  private val serverInterpreter =
    Http4sServerInterpreter(ServerConfiguration.options)

  private val createApplicationRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.createApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (tenantId, request) = input
          applicationService.createApplication(tenantId, request).map {

            case Right(newApplication) =>
              (StatusCode.Created, CreateApplicationResponse(newApplication)).asRight

            case Left(_: ApplicationInsertionError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val updateApplicationRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.updateApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => input =>
          val (applicationId, request) = input
          applicationService.updateApplication(applicationId, request).map {

            case Right(updatedApplication) =>
              (StatusCode.Ok, UpdateApplicationResponse(updatedApplication)).asRight

            case Left(_: ApplicationNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound)).asLeft
          }
        }
    )

  private val reactivateApplicationRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.reactivateApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => applicationId =>
          applicationService.reactivateApplication(applicationId).map {

            case Right(reactivatedApplication) =>
              (StatusCode.Ok, ReactivateApplicationResponse(reactivatedApplication)).asRight

            case Left(_: ApplicationNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound)).asLeft
          }
        }
    )

  private val deactivateApplicationRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.deactivateApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => applicationId =>
          applicationService.deactivateApplication(applicationId).map {

            case Right(deactivatedApplication) =>
              (StatusCode.Ok, DeactivateApplicationResponse(deactivatedApplication)).asRight

            case Left(_: ApplicationNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound)).asLeft
          }
        }
    )

  private val deleteApplicationRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.deleteApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.WriteAdmin))(_))
        .serverLogic { _ => applicationId =>
          applicationService.deleteApplication(applicationId).map {

            case Right(deletedApplication) =>
              (StatusCode.Ok, DeleteApplicationResponse(deletedApplication)).asRight

            case Left(_: ApplicationNotFoundError) =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound)).asLeft

            case Left(_: ApplicationIsNotDeactivatedError) =>
              ErrorInfo
                .badRequestErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationIsNotDeactivated(applicationId)))
                .asLeft

            case Left(_: ApplicationDbError) =>
              ErrorInfo.internalServerErrorInfo().asLeft
          }
        }
    )

  private val getSingleApplicationRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.getSingleApplicationEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => applicationId =>
          applicationService.getBy(applicationId).map {
            case Some(application) => (StatusCode.Ok, GetSingleApplicationResponse(application)).asRight
            case None =>
              ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound)).asLeft
          }
        }
    )

  private val searchApplicationsRoutes: HttpRoutes[IO] =
    serverInterpreter.toRoutes(
      AdminApplicationEndpoints.searchApplicationsEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => tenantId =>
          applicationService.getAllForTenant(tenantId).map { applications =>
            (StatusCode.Ok, GetMultipleApplicationsResponse(applications)).asRight
          }
        }
    )

  val allRoutes: HttpRoutes[IO] =
    createApplicationRoutes <+>
      updateApplicationRoutes <+>
      reactivateApplicationRoutes <+>
      deactivateApplicationRoutes <+>
      deleteApplicationRoutes <+>
      getSingleApplicationRoutes <+>
      searchApplicationsRoutes

}
