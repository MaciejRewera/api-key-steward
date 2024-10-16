package apikeysteward.routes.definitions

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.definitions.EndpointsBase.applicationIdPathParameter
import apikeysteward.routes.model.admin.permission._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[routes] object AdminPermissionEndpoints {

  private val permissionIdPathParameter: EndpointInput.PathCapture[PermissionId] =
    path[PermissionId]("permissionId").description("Unique ID of the Permission.")

  private val nameFragmentQueryParam: EndpointInput.Query[Option[String]] = query[Option[String]]("name")

  val createPermissionEndpoint: Endpoint[
    AccessToken,
    (ApplicationId, CreatePermissionRequest),
    ErrorInfo,
    (StatusCode, CreatePermissionResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create a new Permission with provided details.")
      .in("admin" / "applications" / applicationIdPathParameter / "permissions")
      .in(
        jsonBody[CreatePermissionRequest]
          .description("Details of the Permission to create.")
          .example(EndpointsBase.createPermissionRequest)
      )
      .out(statusCode.description(StatusCode.Created, "Permission created successfully"))
      .out(
        jsonBody[CreatePermissionResponse]
          .example(CreatePermissionResponse(EndpointsBase.PermissionExample))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)
      .errorOutVariantPrepend(errorOutVariantNotFound)

  val deletePermissionEndpoint
      : Endpoint[AccessToken, (ApplicationId, PermissionId), ErrorInfo, (StatusCode, DeletePermissionResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete a Permission.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in("admin" / "applications" / applicationIdPathParameter / "permissions" / permissionIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Permission deleted"))
      .out(
        jsonBody[DeletePermissionResponse]
          .example(DeletePermissionResponse(EndpointsBase.PermissionExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSinglePermissionEndpoint: Endpoint[
    AccessToken,
    (ApplicationId, PermissionId),
    ErrorInfo,
    (StatusCode, GetSinglePermissionResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single Permission for provided permissionId.")
      .in("admin" / "applications" / applicationIdPathParameter / "permissions" / permissionIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "Permission found"))
      .out(
        jsonBody[GetSinglePermissionResponse]
          .example(GetSinglePermissionResponse(EndpointsBase.PermissionExample))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val searchPermissionsEndpoint: Endpoint[
    AccessToken,
    (ApplicationId, Option[String]),
    ErrorInfo,
    (StatusCode, GetMultiplePermissionsResponse),
    Any
  ] =
    EndpointsBase.authenticatedEndpointBase.get
      .description(
        "Search for Permissions based on provided query parameters. Search is scoped to Application with provided applicationId."
      )
      .in("admin" / "applications" / applicationIdPathParameter / "permissions")
      .in(nameFragmentQueryParam)
      .out(statusCode.description(StatusCode.Ok, "Permissions found"))
      .out(
        jsonBody[GetMultiplePermissionsResponse]
          .example(GetMultiplePermissionsResponse(permissions = List.fill(3)(EndpointsBase.PermissionExample)))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

}
