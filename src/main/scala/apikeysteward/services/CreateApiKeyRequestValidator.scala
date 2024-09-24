package apikeysteward.services

import apikeysteward.config.ApiKeyConfig
import apikeysteward.model.CustomError
import apikeysteward.routes.model.admin.UpdateApiKeyRequest
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateUpdateApiKeyRequestBase}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError._
import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.implicits.catsSyntaxTuple2Semigroupal

import scala.concurrent.duration.FiniteDuration

class CreateApiKeyRequestValidator(apiKeyConfig: ApiKeyConfig) {

  def validateCreateRequest(
      createApiKeyRequest: CreateApiKeyRequest
  ): Either[NonEmptyChain[CreateApiKeyRequestValidatorError], CreateApiKeyRequest] =
    (
      validateScopes(createApiKeyRequest),
      validateTimeToLive(createApiKeyRequest)
    ).mapN((_, _) => createApiKeyRequest).toEither

  private def validateScopes(
      createApiKeyRequest: CreateApiKeyRequest
  ): ValidatedNec[CreateApiKeyRequestValidatorError, CreateApiKeyRequest] = {
    val notAllowedScopes = createApiKeyRequest.scopes.toSet.diff(apiKeyConfig.allowedScopes)

    Validated
      .condNec(
        notAllowedScopes.isEmpty,
        createApiKeyRequest,
        NotAllowedScopesProvidedError(notAllowedScopes)
      )
  }

  private def validateTimeToLive(
      createApiKeyRequest: CreateApiKeyRequest
  ): ValidatedNec[CreateApiKeyRequestValidatorError, CreateApiKeyRequest] =
    Validated
      .condNec(
        FiniteDuration(createApiKeyRequest.ttl, ApiKeyExpirationCalculator.ttlTimeUnit) <= apiKeyConfig.ttlMax,
        createApiKeyRequest,
        TtlTooLargeError(createApiKeyRequest.ttl, apiKeyConfig.ttlMax)
      )
}

object CreateApiKeyRequestValidator {

  sealed abstract class CreateApiKeyRequestValidatorError(override val message: String) extends CustomError
  object CreateApiKeyRequestValidatorError {

    case class NotAllowedScopesProvidedError(notAllowedScopes: Set[String])
        extends CreateApiKeyRequestValidatorError(
          message = s"Provided request contains not allowed scopes: [${notAllowedScopes.mkString("'", "', '", "'")}]."
        )

    case class TtlTooLargeError(ttlRequest: Int, ttlMax: FiniteDuration)
        extends CreateApiKeyRequestValidatorError(
          message =
            s"Provided request contains time-to-live (ttl) of: $ttlRequest ${ApiKeyExpirationCalculator.ttlTimeUnit.toString.toLowerCase} which is bigger than maximum allowed value of: $ttlMax."
        )
  }

}
