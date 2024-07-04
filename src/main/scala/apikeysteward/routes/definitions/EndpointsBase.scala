package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.ErrorInfo._
import apikeysteward.routes.auth.JwtAuthorizer.AccessToken
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

private[definitions] object EndpointsBase {

  val errorOutVariantInternalServerError: EndpointOutput.OneOfVariant[ErrorInfo] =
    oneOfVariantValueMatcher(
      StatusCode.InternalServerError,
      jsonBody[ErrorInfo].description("An unexpected error has occurred.")
    ) { case errorInfo: ErrorInfo => errorInfo.error == ErrorDescriptions.InternalServerError }

  val errorOutVariantBadRequest: EndpointOutput.OneOfVariant[ErrorInfo] =
    oneOfVariantValueMatcher(
      StatusCode.BadRequest,
      jsonBody[ErrorInfo].description(ApiErrorMessages.General.BadRequest)
    ) { case errorInfo: ErrorInfo => errorInfo.error == ErrorDescriptions.BadRequest }

  val errorOutVariantForbidden: EndpointOutput.OneOfVariant[ErrorInfo] =
    oneOfVariantValueMatcher(
      StatusCode.Forbidden,
      jsonBody[ErrorInfo].description(ApiErrorMessages.General.Unauthorized)
    ) { case errorInfo: ErrorInfo => errorInfo.error == ErrorDescriptions.Forbidden }

  val errorOutVariantUnauthorized: EndpointOutput.OneOfVariant[ErrorInfo] =
    oneOfVariantValueMatcher(
      StatusCode.Unauthorized,
      jsonBody[ErrorInfo].description(ApiErrorMessages.General.Unauthorized)
    ) { case errorInfo: ErrorInfo => errorInfo.error == ErrorDescriptions.Unauthorized }

  val errorOutVariantNotFound: EndpointOutput.OneOfVariant[ErrorInfo] =
    oneOfVariantValueMatcher(
      StatusCode.NotFound,
      jsonBody[ErrorInfo].description(ApiErrorMessages.Admin.DeleteApiKeyNotFound)
    ) { case errorInfo: ErrorInfo => errorInfo.error == ErrorDescriptions.NotFound }

  val authenticatedEndpointBase: Endpoint[AccessToken, Unit, ErrorInfo, Unit, Any] =
    endpoint
      .securityIn(auth.bearer[AccessToken]())
      .errorOut(oneOf[ErrorInfo](errorOutVariantInternalServerError, errorOutVariantUnauthorized))
}
