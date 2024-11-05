package apikeysteward.routes

import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.{AdminApiKeyTemplateEndpoints, ApiErrorMessages}
import apikeysteward.routes.model.admin.apikeytemplate._
import apikeysteward.services.ApiKeyTemplateService
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AdminApiKeyTemplateRoutes(jwtAuthorizer: JwtAuthorizer, apiKeyTemplateService: ApiKeyTemplateService) {

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
      AdminApiKeyTemplateEndpoints.getSingleApplicationEndpoint
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
      AdminApiKeyTemplateEndpoints.searchApplicationsEndpoint
        .serverSecurityLogic(jwtAuthorizer.authorisedWithPermissions(Set(JwtPermissions.ReadAdmin))(_))
        .serverLogic { _ => tenantId =>
          apiKeyTemplateService.getAllForTenant(tenantId).map { apiKeyTemplates =>
            (StatusCode.Ok, GetMultipleApiKeyTemplatesResponse(apiKeyTemplates)).asRight
          }
        }
    )

  val allRoutes: HttpRoutes[IO] =
    createApiKeyTemplateRoutes <+>
      updateApiKeyTemplateRoutes <+>
      deleteApiKeyTemplateRoutes <+>
      getSingleApiKeyTemplateRoutes <+>
      searchApiKeyTemplateRoutes

}
