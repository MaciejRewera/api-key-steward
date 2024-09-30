package apikeysteward.routes.definitions

import apikeysteward.model.{ApiKey, ApiKeyData, Tenant}
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.ErrorInfo._
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import apikeysteward.routes.definitions.EndpointsBase.ErrorOutputVariants._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.time.Instant
import java.util.UUID

private[routes] object EndpointsBase {

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
          .description(ApiErrorMessages.Admin.DeleteApiKeyNotFound)
          .example(notFoundErrorInfo(Some(ApiErrorMessages.Admin.DeleteApiKeyNotFound)))
      ) { case errorInfo: ErrorInfo => errorInfo.error == Errors.NotFound }
  }

  val ApiKeyDataExample: ApiKeyData = ApiKeyData(
    publicKeyId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "My API key",
    description = Some("A short description what this API key is for."),
    userId = "user-1234567",
    expiresAt = Instant.parse("2024-06-03T13:34:56.789098Z"),
    scopes = List("read:myApi", "write:myApi")
  )

  val ApiKeyExample: ApiKey = ApiKey("prefix_thisIsMyApiKey1234567")

  val TenantExample: Tenant = Tenant(
    tenantId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
    name = "My new Tenant",
    isActive = true
  )

  val authenticatedEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, Unit, Any] =
    endpoint
      .securityIn(auth.bearer[AccessToken]())
      .errorOut(oneOf[ErrorInfo](errorOutVariantUnauthorized, errorOutVariantInternalServerError))
}
