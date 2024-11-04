package apikeysteward.routes.definitions

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.definitions.EndpointsBase.tenantIdHeaderInput
import apikeysteward.routes.model.admin.apikeytemplate._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

private[routes] object AdminApiKeyTemplateEndpoints {

  private val templateIdPathParameter: EndpointInput.PathCapture[ApplicationId] =
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
    (ApiKeyTemplateId, UpdateApiKeyTemplateRequest),
    ErrorInfo,
    (StatusCode, UpdateApiKeyTemplateResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.put
      .description(
        """Update an existing Template. You have to specify all of the fields of the Template.
          |This API replaces the existing Template with your new data.""".stripMargin
      )
      .in("admin" / "templates" / templateIdPathParameter)
      .in(
        jsonBody[UpdateApiKeyTemplateRequest]
          .description("Details of the Template to update.")
          .example(
            UpdateApiKeyTemplateRequest(name = "Basic API key", description = Some("API Key Template with basic set of available permissions."), isDefault = true, apiKeyMaxExpiryPeriod = Duration.apply(42, TimeUnit.DAYS))
          )
      )
      .out(statusCode.description(StatusCode.Ok, "Template updated successfully."))
      .out(
        jsonBody[UpdateApiKeyTemplateResponse]
          .example(UpdateApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteApplicationEndpoint
      : Endpoint[AccessToken, ApiKeyTemplateId, ErrorInfo, (StatusCode, DeleteApiKeyTemplateResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete a Template.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in("admin" / "templates" / templateIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Template deleted."))
      .out(
        jsonBody[DeleteApiKeyTemplateResponse]
          .example(DeleteApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleApplicationEndpoint
      : Endpoint[AccessToken, ApiKeyTemplateId, ErrorInfo, (StatusCode, GetSingleApiKeyTemplateResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single Template for provided templateId.")
      .in("admin" / "templates" / templateIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Template found."))
      .out(
        jsonBody[GetSingleApiKeyTemplateResponse]
          .example(GetSingleApiKeyTemplateResponse(EndpointsBase.ApiKeyTemplateExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val searchApplicationsEndpoint
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

}
