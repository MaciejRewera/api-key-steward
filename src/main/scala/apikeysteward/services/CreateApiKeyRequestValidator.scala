package apikeysteward.services

import apikeysteward.config.ApiKeyConfig
import apikeysteward.model.CustomError
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError._
import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.implicits.catsSyntaxTuple2Semigroupal

class CreateApiKeyRequestValidator(apiKeyConfig: ApiKeyConfig) {

  def validateRequest(
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
        createApiKeyRequest.ttl <= apiKeyConfig.ttlMax,
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

    case class TtlTooLargeError(ttlRequest: Int, ttlMax: Int)
        extends CreateApiKeyRequestValidatorError(
          message =
            s"Provided request contains time-to-live (ttl) of: $ttlRequest which is bigger than maximum allowed value of: $ttlMax."
        )
  }

}
