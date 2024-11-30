package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.definitions.EndpointsBase.{UserExample_1, UserExample_2, UserExample_3, tenantIdHeaderInput}
import apikeysteward.routes.model.admin.apikeytemplate._
import apikeysteward.routes.model.admin.apikeytemplatespermissions.{
  CreateApiKeyTemplatesPermissionsRequest,
  DeleteApiKeyTemplatesPermissionsRequest
}
import apikeysteward.routes.model.admin.apikeytemplatesusers.AssociateUsersWithApiKeyTemplateRequest
import apikeysteward.routes.model.admin.permission.GetMultiplePermissionsResponse
import apikeysteward.routes.model.admin.user.GetMultipleUsersResponse
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

private[routes] object AdminApiKeyTemplateEndpoints {

  private val templateIdPathParameter: EndpointInput.PathCapture[ApiKeyTemplateId] =
    path[ApiKeyTemplateId]("templateId").description("Unique ID of the Template.")

  val createApiKeyTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, CreateApiKeyTemplateRequest),
    ErrorInfo,
    (StatusCode, CreateApiKeyTemplateResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create a new Template with provided details.")
      .in(tenantIdHeaderInput)
      .in("admin" / "templates")
      .in(
        jsonBody[CreateApiKeyTemplateRequest]
          .description("Details of the Template to create.")
          .example(EndpointsBase.createApiKeyTemplateRequest)
      )
      .out(statusCode.description(StatusCode.Created, "Template created successfully"))
      .out(
        jsonBody[CreateApiKeyTemplateResponse]
          .example(CreateApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val updateApiKeyTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId, UpdateApiKeyTemplateRequest),
    ErrorInfo,
    (StatusCode, UpdateApiKeyTemplateResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        """Update an existing Template. You have to specify all of the fields of the Template.
          |This API replaces the existing Template with your new data.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter)
      .in(
        jsonBody[UpdateApiKeyTemplateRequest]
          .description("Details of the Template to update.")
          .example(EndpointsBase.updateApiKeyTemplateRequest)
      )
      .out(statusCode.description(StatusCode.Ok, "Template updated successfully."))
      .out(
        jsonBody[UpdateApiKeyTemplateResponse]
          .example(UpdateApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteResourceServerEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId),
    ErrorInfo,
    (StatusCode, DeleteApiKeyTemplateResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete a Template.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Template deleted."))
      .out(
        jsonBody[DeleteApiKeyTemplateResponse]
          .example(DeleteApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleApiKeyTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId),
    ErrorInfo,
    (StatusCode, GetSingleApiKeyTemplateResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single Template for provided templateId.")
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Template found."))
      .out(
        jsonBody[GetSingleApiKeyTemplateResponse]
          .example(GetSingleApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val searchApiKeyTemplatesEndpoint
      : Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, GetMultipleApiKeyTemplatesResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Templates for provided tenantId.")
      .in(tenantIdHeaderInput)
      .in("admin" / "templates")
      .out(statusCode.description(StatusCode.Ok, "Templates found."))
      .out(
        jsonBody[GetMultipleApiKeyTemplatesResponse]
          .example(GetMultipleApiKeyTemplatesResponse(templates = List.fill(3)(EndpointsBase.ApiKeyTemplateExample)))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val associatePermissionsWithApiKeyTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId, CreateApiKeyTemplatesPermissionsRequest),
    ErrorInfo,
    StatusCode,
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("""Associate Permissions with a Template.
                     |Add one or more Permissions to the specified Template.
          """.stripMargin)
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter / "permissions")
      .in(
        jsonBody[CreateApiKeyTemplatesPermissionsRequest]
          .example(
            CreateApiKeyTemplatesPermissionsRequest(
              List(
                UUID.fromString("f877c2e5-f820-4e84-a706-919a630337ec"),
                UUID.fromString("af7b4c89-481a-4ab7-ad10-b976615a0de2"),
                UUID.fromString("06022369-8ca1-43c3-ab49-d176cc1803d9")
              )
            )
          )
      )
      .out(statusCode.description(StatusCode.Created, "Permissions successfully associated with the Template."))
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val removePermissionsFromApiKeyTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId, DeleteApiKeyTemplatesPermissionsRequest),
    ErrorInfo,
    StatusCode,
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Remove Permissions from a Template.
          |Remove one or more Permissions from the specified Template.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter / "permissions")
      .in(
        jsonBody[DeleteApiKeyTemplatesPermissionsRequest]
          .example(
            DeleteApiKeyTemplatesPermissionsRequest(
              List(
                UUID.fromString("f877c2e5-f820-4e84-a706-919a630337ec"),
                UUID.fromString("af7b4c89-481a-4ab7-ad10-b976615a0de2"),
                UUID.fromString("06022369-8ca1-43c3-ab49-d176cc1803d9")
              )
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "Permissions successfully removed from the Template."))
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllPermissionsForTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId),
    ErrorInfo,
    (StatusCode, GetMultiplePermissionsResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Permissions associated with the specified Template.")
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter / "permissions")
      .out(statusCode.description(StatusCode.Ok, "Permissions found."))
      .out(
        jsonBody[GetMultiplePermissionsResponse]
          .example(GetMultiplePermissionsResponse(permissions = List.fill(3)(EndpointsBase.PermissionExample)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)

  val associateUsersWithApiKeyTemplateEndpoint: Endpoint[
    AccessToken,
    (TenantId, ApiKeyTemplateId, AssociateUsersWithApiKeyTemplateRequest),
    ErrorInfo,
    StatusCode,
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.post
      .description(
        """Associate Users with a Template.
          |Add one or more Users to the specified Template.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter / "users")
      .in(
        jsonBody[AssociateUsersWithApiKeyTemplateRequest]
          .example(
            AssociateUsersWithApiKeyTemplateRequest(
              List(
                UserExample_1.userId,
                UserExample_2.userId,
                UserExample_3.userId
              )
            )
          )
      )
      .out(statusCode.description(StatusCode.Created, "Users successfully associated with the Template."))
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllUsersForTemplateEndpoint
      : Endpoint[AccessToken, (TenantId, ApiKeyTemplateId), ErrorInfo, (StatusCode, GetMultipleUsersResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Users associated with the specified Template.")
      .in(tenantIdHeaderInput)
      .in("admin" / "templates" / templateIdPathParameter / "users")
      .out(statusCode.description(StatusCode.Ok, "Users found."))
      .out(
        jsonBody[GetMultipleUsersResponse]
          .example(GetMultipleUsersResponse(List(UserExample_1, UserExample_2, UserExample_3)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)

}
