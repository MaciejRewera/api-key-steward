package apikeysteward.routes.definitions

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.definitions.EndpointsBase._
import apikeysteward.routes.model.admin.application._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[routes] object AdminApplicationEndpoints {

  val createApplicationEndpoint: Endpoint[
    AccessToken,
    (TenantId, CreateApplicationRequest),
    ErrorInfo,
    (StatusCode, CreateApplicationResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create a new Application with provided details.")
      .in(tenantIdHeaderInput)
      .in("admin" / "applications")
      .in(
        jsonBody[CreateApplicationRequest]
          .description("Details of the Application to create.")
          .example(EndpointsBase.createApplicationRequest)
      )
      .out(statusCode.description(StatusCode.Created, "Application created successfully"))
      .out(
        jsonBody[CreateApplicationResponse]
          .example(CreateApplicationResponse(EndpointsBase.ApplicationExample))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val updateApplicationEndpoint: Endpoint[
    AccessToken,
    (ApplicationId, UpdateApplicationRequest),
    ErrorInfo,
    (StatusCode, UpdateApplicationResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        """Update an existing Application. You have to specify all of the fields of the Application.
          |This API replaces the existing Application with your new data.""".stripMargin
      )
      .in("admin" / "applications" / applicationIdPathParameter)
      .in(
        jsonBody[UpdateApplicationRequest]
          .description("Details of the Application to update.")
          .example(
            UpdateApplicationRequest(
              name = "My new Application",
              description = Some("A description what this Application is for.")
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "Application updated successfully"))
      .out(
        jsonBody[UpdateApplicationResponse]
          .example(UpdateApplicationResponse(EndpointsBase.ApplicationExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val reactivateApplicationEndpoint
      : Endpoint[AccessToken, ApplicationId, ErrorInfo, (StatusCode, ReactivateApplicationResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description("Reactivate an inactive Application. If the Application is already active, this API has no effect.")
      .in("admin" / "applications" / applicationIdPathParameter / "reactivation")
      .out(statusCode.description(StatusCode.Ok, "Application reactivated"))
      .out(
        jsonBody[ReactivateApplicationResponse]
          .example(ReactivateApplicationResponse(EndpointsBase.ApplicationExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deactivateApplicationEndpoint
      : Endpoint[AccessToken, ApplicationId, ErrorInfo, (StatusCode, DeactivateApplicationResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description("Deactivate an active Application. If the Application is already inactive, this API has no effect.")
      .in("admin" / "applications" / applicationIdPathParameter / "deactivation")
      .out(statusCode.description(StatusCode.Ok, "Application deactivated"))
      .out(
        jsonBody[DeactivateApplicationResponse]
          .example(DeactivateApplicationResponse(EndpointsBase.ApplicationExample.copy(isActive = false)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteApplicationEndpoint
      : Endpoint[AccessToken, ApplicationId, ErrorInfo, (StatusCode, DeleteApplicationResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete an inactive Application. The Application has to be inactive before using this API.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in("admin" / "applications" / applicationIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Application deleted"))
      .out(
        jsonBody[DeleteApplicationResponse]
          .example(DeleteApplicationResponse(EndpointsBase.ApplicationExample.copy(isActive = false)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleApplicationEndpoint
      : Endpoint[AccessToken, ApplicationId, ErrorInfo, (StatusCode, GetSingleApplicationResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single Application for provided applicationId.")
      .in("admin" / "applications" / applicationIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Application found"))
      .out(
        jsonBody[GetSingleApplicationResponse]
          .example(GetSingleApplicationResponse(EndpointsBase.ApplicationExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val searchApplicationsEndpoint
      : Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, GetMultipleApplicationsResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Applications for provided tenantId.")
      .in(tenantIdHeaderInput)
      .in("admin" / "applications")
      .out(statusCode.description(StatusCode.Ok, "Applications found"))
      .out(
        jsonBody[GetMultipleApplicationsResponse]
          .example(GetMultipleApplicationsResponse(applications = List.fill(3)(EndpointsBase.ApplicationExample)))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

}
