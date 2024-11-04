package apikeysteward.routes.definitions

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model._
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.ErrorInfo._
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import apikeysteward.routes.model.admin.apikeytemplate.CreateApiKeyTemplateRequest
import apikeysteward.routes.model.admin.application.CreateApplicationRequest
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import apikeysteward.routes.model.admin.user.CreateUserRequest
import org.typelevel.ci.{CIString, CIStringSyntax}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

private[routes] object EndpointsBase {

  val tenantIdHeaderName: CIString = ci"ApiKeySteward-TenantId"
  val tenantIdHeaderInput: EndpointInput[TenantId] = header[TenantId](tenantIdHeaderName.toString)
    .description("Unique ID of the Tenant for which to scope this request.")

  val applicationIdPathParameter: EndpointInput.PathCapture[ApplicationId] =
    path[ApplicationId]("applicationId").description("Unique ID of the Application.")

  val createApiKeyTemplateRequest: CreateApiKeyTemplateRequest = CreateApiKeyTemplateRequest(name = "Basic API key", description = Some("API Key Template with basic set of available permissions."), isDefault = false, apiKeyMaxExpiryPeriod = Duration.apply(42, TimeUnit.DAYS))

  val createPermissionRequest: CreatePermissionRequest = CreatePermissionRequest(
    name = "read:permission:123",
    description = Some("A description what this Permission is for.")
  )

  val createApplicationRequest: CreateApplicationRequest = CreateApplicationRequest(
    name = "My new Application",
    description = Some("A description what this Application is for."),
    permissions = List(EndpointsBase.createPermissionRequest)
  )

  val createUserRequest: CreateUserRequest = CreateUserRequest(userId = "user-1234567")

  val ApiKeyExample: ApiKey = ApiKey("prefix_thisIsMyApiKey1234567")

  val ApiKeyDataExample: ApiKeyData = ApiKeyData(
    publicKeyId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "My API key",
    description = Some("A short description what this API key is for."),
    userId = "user-1234567",
    expiresAt = Instant.parse("2024-06-03T13:34:56.789098Z")
  )

  val ApiKeyTemplateExample: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "Basic API key",
    description = Some("API Key Template with basic set of available permissions."),
    isDefault = false,
    apiKeyMaxExpiryPeriod = Duration.apply(42, TimeUnit.DAYS)
  )

  val TenantExample: Tenant = Tenant(
    tenantId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "My new Tenant",
    description = Some("A description what this Tenant is for."),
    isActive = true
  )

  val PermissionExample: Permission = Permission(
    permissionId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "read:permission:123",
    description = Some("A description what this Permission is for.")
  )

  val ApplicationExample: Application = Application(
    applicationId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "My new Application",
    description = Some("A description what this Application is for."),
    isActive = true,
    permissions = List(PermissionExample, PermissionExample)
  )

  val UserExample_1: User = User("user-123456701")
  val UserExample_2: User = User("user-123456702")
  val UserExample_3: User = User("user-123456703")

  val authenticatedEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, Unit, Any] =
    endpoint
      .securityIn(auth.bearer[AccessToken]())
      .errorOut(oneOf[ErrorInfo](errorOutVariantUnauthorized, errorOutVariantInternalServerError))

  object ErrorOutputVariants {

    val errorOutVariantInternalServerError: EndpointOutput.OneOfVariant[ErrorInfo] =
      oneOfVariantValueMatcher(
        StatusCode.InternalServerError,
        jsonBody[ErrorInfo]
          .description(ApiErrorMessages.General.InternalServerError)
          .example(internalServerErrorInfo(Some(ApiErrorMessages.General.InternalServerError)))
      ) { case errorInfo: ErrorInfo => errorInfo.error == Errors.InternalServerError }

    val errorOutVariantBadRequest: EndpointOutput.OneOfVariant[ErrorInfo] =
      oneOfVariantValueMatcher(
        StatusCode.BadRequest,
        jsonBody[ErrorInfo]
          .description(ApiErrorMessages.General.BadRequest)
          .example(badRequestErrorInfo(Some(ApiErrorMessages.General.BadRequest)))
      ) { case errorInfo: ErrorInfo => errorInfo.error == Errors.BadRequest }

    val errorOutVariantForbidden: EndpointOutput.OneOfVariant[ErrorInfo] =
      oneOfVariantValueMatcher(
        StatusCode.Forbidden,
        jsonBody[ErrorInfo]
          .description(ApiErrorMessages.General.Unauthorized)
          .example(forbiddenErrorInfo(Some("Provided API Key is expired since: 2024-06-03T12:34:56.789098Z.")))
      ) { case errorInfo: ErrorInfo => errorInfo.error == Errors.Forbidden }

    val errorOutVariantUnauthorized: EndpointOutput.OneOfVariant[ErrorInfo] =
      oneOfVariantValueMatcher(
        StatusCode.Unauthorized,
        jsonBody[ErrorInfo]
          .description(ApiErrorMessages.General.Unauthorized)
          .example(
            unauthorizedErrorInfo(
              Some("Exception occurred while decoding JWT: The token is expired since 2024-06-26T12:34:56Z")
            )
          )
      ) { case errorInfo: ErrorInfo => errorInfo.error == Errors.Unauthorized }

    val errorOutVariantNotFound: EndpointOutput.OneOfVariant[ErrorInfo] =
      oneOfVariantValueMatcher(
        StatusCode.NotFound,
        jsonBody[ErrorInfo]
          .description(ApiErrorMessages.General.NotFound)
          .example(notFoundErrorInfo(Some(ApiErrorMessages.General.NotFound)))
      ) { case errorInfo: ErrorInfo => errorInfo.error == Errors.NotFound }
  }

}
