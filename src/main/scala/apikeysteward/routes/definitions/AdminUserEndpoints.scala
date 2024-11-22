package apikeysteward.routes.definitions

import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants.{
  errorOutVariantBadRequest,
  errorOutVariantNotFound
}
import apikeysteward.routes.definitions.EndpointsBase.{
  ApiKeyTemplateExample,
  UserExample_1,
  UserExample_2,
  UserExample_3,
  tenantIdHeaderInput
}
import apikeysteward.routes.model.admin.apikeytemplate.GetMultipleApiKeyTemplatesResponse
import apikeysteward.routes.model.admin.apikeytemplatesusers.{
  AssociateApiKeyTemplatesWithUserRequest,
  AssociateUsersWithApiKeyTemplateRequest,
  DeleteApiKeyTemplatesFromUserRequest
}
import apikeysteward.routes.model.admin.user._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

private[routes] object AdminUserEndpoints {

  private val userIdPathParameter =
    path[UserId]("userId").description("ID of the User. It has to be unique per Tenant.")

  val createUserEndpoint
      : Endpoint[AccessToken, (TenantId, CreateUserRequest), ErrorInfo, (StatusCode, CreateUserResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .description("Create a new User with provided details.")
      .in(tenantIdHeaderInput)
      .in("admin" / "users")
      .in(
        jsonBody[CreateUserRequest]
          .description("Details of the User to create.")
          .example(EndpointsBase.createUserRequest)
      )
      .out(statusCode.description(StatusCode.Created, "User created successfully"))
      .out(
        jsonBody[CreateUserResponse]
          .example(CreateUserResponse(EndpointsBase.UserExample_1))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val deleteUserEndpoint: Endpoint[AccessToken, (TenantId, UserId), ErrorInfo, (StatusCode, DeleteUserResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Delete a User.
          |
          |This operation is permanent. Proceed with caution.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "users" / userIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "User deleted"))
      .out(
        jsonBody[DeleteUserResponse]
          .example(DeleteUserResponse(EndpointsBase.UserExample_1))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getSingleUserEndpoint
      : Endpoint[AccessToken, (TenantId, UserId), ErrorInfo, (StatusCode, GetSingleUserResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get single User for provided tenantId and userId.")
      .in(tenantIdHeaderInput)
      .in("admin" / "users" / userIdPathParameter)
      .out(statusCode.description(StatusCode.Ok, "User found."))
      .out(
        jsonBody[GetSingleUserResponse]
          .example(GetSingleUserResponse(UserExample_1))
      )
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val searchUsersEndpoint: Endpoint[AccessToken, TenantId, ErrorInfo, (StatusCode, GetMultipleUsersResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Users for provided tenantId.")
      .in(tenantIdHeaderInput)
      .in("admin" / "users")
      .out(statusCode.description(StatusCode.Ok, "Users found"))
      .out(
        jsonBody[GetMultipleUsersResponse]
          .example(GetMultipleUsersResponse(List(UserExample_1, UserExample_2, UserExample_3)))
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val associateApiKeyTemplatesWithUserEndpoint
      : Endpoint[AccessToken, (TenantId, UserId, AssociateApiKeyTemplatesWithUserRequest), ErrorInfo, StatusCode, Any] =
    EndpointsBase.authenticatedEndpointBase.post
      .description(
        """Associate Templates with a User.
          |Add one or more Templates to the specified User.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "users" / userIdPathParameter / "templates")
      .in(
        jsonBody[AssociateApiKeyTemplatesWithUserRequest]
          .example(
            AssociateApiKeyTemplatesWithUserRequest(
              List(
                UUID.fromString("17365bf0-445b-4dc6-9f70-fce06ae0408d"),
                UUID.fromString("962c5563-0995-43c2-83aa-845dbc43b8cc"),
                UUID.fromString("cb74ad50-14db-4f84-90ae-9753aba99477")
              )
            )
          )
      )
      .out(statusCode.description(StatusCode.Created, "Templates successfully associated with the User."))
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val removeApiKeyTemplatesFromUserEndpoint
      : Endpoint[AccessToken, (TenantId, UserId, DeleteApiKeyTemplatesFromUserRequest), ErrorInfo, StatusCode, Any] =
    EndpointsBase.authenticatedEndpointBase.delete
      .description(
        """Remove Templates from a User.
          |Remove one or more Templates from the specified User.""".stripMargin
      )
      .in(tenantIdHeaderInput)
      .in("admin" / "users" / userIdPathParameter / "templates")
      .in(
        jsonBody[DeleteApiKeyTemplatesFromUserRequest]
          .example(
            DeleteApiKeyTemplatesFromUserRequest(
              List(
                UUID.fromString("17365bf0-445b-4dc6-9f70-fce06ae0408d"),
                UUID.fromString("962c5563-0995-43c2-83aa-845dbc43b8cc"),
                UUID.fromString("cb74ad50-14db-4f84-90ae-9753aba99477")
              )
            )
          )
      )
      .out(statusCode.description(StatusCode.Ok, "Templates successfully removed from the User."))
      .errorOutVariantPrepend(errorOutVariantNotFound)
      .errorOutVariantPrepend(errorOutVariantBadRequest)

  val getAllApiKeyTemplatesForUserEndpoint
      : Endpoint[AccessToken, (TenantId, UserId), ErrorInfo, (StatusCode, GetMultipleApiKeyTemplatesResponse), Any] =
    EndpointsBase.authenticatedEndpointBase.get
      .description("Get all Templates associated with the specified User.")
      .in(tenantIdHeaderInput)
      .in("admin" / "users" / userIdPathParameter / "templates")
      .out(statusCode.description(StatusCode.Ok, "Templates found."))
      .out(
        jsonBody[GetMultipleApiKeyTemplatesResponse]
          .example(
            GetMultipleApiKeyTemplatesResponse(
              List(ApiKeyTemplateExample, ApiKeyTemplateExample, ApiKeyTemplateExample)
            )
          )
      )
      .errorOutVariantPrepend(errorOutVariantBadRequest)

}
