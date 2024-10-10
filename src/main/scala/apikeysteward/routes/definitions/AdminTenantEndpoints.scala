package apikeysteward.routes.definitions

import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.model.admin.tenant._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[routes] object AdminTenantEndpoints {

  private val tenantIdPathParameter = path[TenantId]("tenantId").description("Unique ID of the Tenant.")

  val createTenantEndpoint
      : Endpoint[AccessToken, CreateTenantRequest, ErrorInfo, (StatusCode, CreateTenantResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create a new Tenant with provided details.")
      .in("admin" / "tenants")
      .in(
        jsonBody[CreateTenantRequest]
          .description("Details of the Tenant to create.")
          .example(
            CreateTenantRequest(name = "My new Tenant", description = Some("A description what this Tenant is for."))
          )
      )
      .out(statusCode.description(StatusCode.Created, "Tenant created successfully"))
      .out(
        jsonBody[CreateTenantResponse]
          .example(CreateTenantResponse(EndpointsBase.TenantExample))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val updateTenantEndpoint
      : Endpoint[AccessToken, (TenantId, UpdateTenantRequest), ErrorInfo, (StatusCode, UpdateTenantResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        """Update an existing Tenant. You have to specify all of the fields of the Tenant.
          |This API replaces the existing Tenant with your new data.""".stripMargin
      )
      .in("admin" / "tenants" / tenantIdPathParameter)
      .in(
        jsonBody[UpdateTenantRequest]
          .description("Details of the Tenant to update.")
          .example(
            UpdateTenantRequest(name = "My new Tenant", description = Some("A description what this Tenant is for."))
          )
      )
      .out(statusCode.description(StatusCode.Ok, "Tenant updated successfully"))
      .out(
        jsonBody[UpdateTenantResponse]
          .example(UpdateTenantResponse(EndpointsBase.TenantExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val reactivateTenantEndpoint
      : Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, ReactivateTenantResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description("Reactivate an inactive Tenant. If the Tenant is already active, this API has no effect.")
      .in("admin" / "tenants" / tenantIdPathParameter / "reactivation")
      .out(statusCode.description(StatusCode.Ok, "Tenant reactivated"))
      .out(
        jsonBody[ReactivateTenantResponse]
          .example(ReactivateTenantResponse(EndpointsBase.TenantExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deactivateTenantEndpoint
      : Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, DeactivateTenantResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.put
      .description("Deactivate an active Tenant. If the Tenant is already inactive, this API has no effect.")
      .in("admin" / "tenants" / tenantIdPathParameter / "deactivation")
      .out(statusCode.description(StatusCode.Ok, "Tenant deactivated"))
      .out(
        jsonBody[DeactivateTenantResponse]
          .example(DeactivateTenantResponse(EndpointsBase.TenantExample.copy(isActive = false)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteTenantEndpoint: Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, DeleteTenantResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete an inactive Tenant. The Tenant has to be inactive before using this API.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in("admin" / "tenants" / tenantIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Tenant deleted"))
      .out(
        jsonBody[DeleteTenantResponse]
          .example(DeleteTenantResponse(EndpointsBase.TenantExample.copy(isActive = false)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleTenantEndpoint: Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, GetSingleTenantResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single Tenant for provided tenantId.")
      .in("admin" / "tenants" / tenantIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Tenant found"))
      .out(
        jsonBody[GetSingleTenantResponse]
          .example(GetSingleTenantResponse(EndpointsBase.TenantExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllTenantsEndpoint: Endpoint[AccessToken, Unit, ErrorInfo, (StatusCode, GetMultipleTenantsResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Tenants.")
      .in("admin" / "tenants")
      .out(statusCode.description(StatusCode.Ok, "Tenants found"))
      .out(
        jsonBody[GetMultipleTenantsResponse]
          .example(GetMultipleTenantsResponse(tenants = List(EndpointsBase.TenantExample)))
      )

}
