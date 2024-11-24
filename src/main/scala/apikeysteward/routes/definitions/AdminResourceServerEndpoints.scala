package apikeysteward.routes.definitions

import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.definitions.EndpointsBase._
import apikeysteward.routes.model.admin.resourceserver._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[routes] object AdminResourceServerEndpoints {

  val createResourceServerEndpoint: Endpoint[
    AccessToken,
    (TenantId, CreateResourceServerRequest),
    ErrorInfo,
    (StatusCode, CreateResourceServerResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create a new Resource Server with provided details.")
      .in(tenantIdHeaderInput)
      .in("admin" / "resource-servers")
      .in(
        jsonBody[CreateResourceServerRequest]
          .description("Details of the Resource Server to create.")
          .example(EndpointsBase.createResourceServerRequest)
      )
      .out(statusCode.description(StatusCode.Created, "Resource Server created successfully"))
      .out(
        jsonBody[CreateResourceServerResponse]
          .example(CreateResourceServerResponse(EndpointsBase.ResourceServerExample))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val updateResourceServerEndpoint: Endpoint[
    AccessToken,
    (ResourceServerId, UpdateResourceServerRequest),
    ErrorInfo,
    (StatusCode, UpdateResourceServerResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        """Update an existing Resource Server. You have to specify all of the fields of the Resource Server.
          |This API replaces the existing Resource Server with your new data.""".stripMargin
      )
      .in("admin" / "resource-servers" / resourceServerIdPathParameter)
      .in(
        jsonBody[UpdateResourceServerRequest]
          .description("Details of the Resource Server to update.")
          .example(
            UpdateResourceServerRequest(
              name = "My new Resource Server",
              description = Some("A description what this Resource Server is for.")
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "Resource Server updated successfully."))
      .out(
        jsonBody[UpdateResourceServerResponse]
          .example(UpdateResourceServerResponse(EndpointsBase.ResourceServerExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val reactivateResourceServerEndpoint
      : Endpoint[AccessToken, ResourceServerId, ErrorInfo, (StatusCode, ReactivateResourceServerResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        "Reactivate an inactive Resource Server. If the Resource Server is already active, this API has no effect."
      )
      .in("admin" / "resource-servers" / resourceServerIdPathParameter / "reactivation")
      .out(statusCode.description(StatusCode.Ok, "Resource Server reactivated."))
      .out(
        jsonBody[ReactivateResourceServerResponse]
          .example(ReactivateResourceServerResponse(EndpointsBase.ResourceServerExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deactivateResourceServerEndpoint
      : Endpoint[AccessToken, ResourceServerId, ErrorInfo, (StatusCode, DeactivateResourceServerResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        "Deactivate an active Resource Server. If the Resource Server is already inactive, this API has no effect."
      )
      .in("admin" / "resource-servers" / resourceServerIdPathParameter / "deactivation")
      .out(statusCode.description(StatusCode.Ok, "Resource Server deactivated."))
      .out(
        jsonBody[DeactivateResourceServerResponse]
          .example(DeactivateResourceServerResponse(EndpointsBase.ResourceServerExample.copy(isActive = false)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteResourceServerEndpoint
      : Endpoint[AccessToken, ResourceServerId, ErrorInfo, (StatusCode, DeleteResourceServerResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete an inactive Resource Server. The Resource Server has to be inactive before using this API.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in("admin" / "resource-servers" / resourceServerIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Resource Server deleted."))
      .out(
        jsonBody[DeleteResourceServerResponse]
          .example(DeleteResourceServerResponse(EndpointsBase.ResourceServerExample.copy(isActive = false)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleResourceServerEndpoint
      : Endpoint[AccessToken, ResourceServerId, ErrorInfo, (StatusCode, GetSingleResourceServerResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single Resource Server for provided resourceServerId.")
      .in("admin" / "resource-servers" / resourceServerIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Resource Server found."))
      .out(
        jsonBody[GetSingleResourceServerResponse]
          .example(GetSingleResourceServerResponse(EndpointsBase.ResourceServerExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val searchResourceServersEndpoint
      : Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, GetMultipleResourceServersResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Resource Servers for provided tenantId.")
      .in(tenantIdHeaderInput)
      .in("admin" / "resource-servers")
      .out(statusCode.description(StatusCode.Ok, "Resource Servers found."))
      .out(
        jsonBody[GetMultipleResourceServersResponse]
          .example(
            GetMultipleResourceServersResponse(resourceServers = List.fill(3)(EndpointsBase.ResourceServerExample))
          )
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

}
