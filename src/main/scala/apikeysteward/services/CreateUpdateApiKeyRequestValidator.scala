package apikeysteward.services

import apikeysteward.config.ApiKeyConfig
import apikeysteward.model.CustomError
import apikeysteward.routes.model.CreateUpdateApiKeyRequestBase
import apikeysteward.services.CreateUpdateApiKeyRequestValidator.CreateUpdateApiKeyRequestValidatorError
import apikeysteward.services.CreateUpdateApiKeyRequestValidator.CreateUpdateApiKeyRequestValidatorError._
import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.implicits.catsSyntaxTuple2Semigroupal

import scala.concurrent.duration.FiniteDuration

class CreateUpdateApiKeyRequestValidator(apiKeyConfig: ApiKeyConfig) {

  def validateRequest[Request <: CreateUpdateApiKeyRequestBase](
      createUpdateApiKeyRequest: Request
  ): Either[NonEmptyChain[CreateUpdateApiKeyRequestValidatorError], Request] =
    (
      validateScopes(createUpdateApiKeyRequest),
      validateTimeToLive(createUpdateApiKeyRequest)
    ).mapN((_, _) => createUpdateApiKeyRequest).toEither

  private def validateScopes(
      createApiKeyRequest: CreateUpdateApiKeyRequestBase
  ): ValidatedNec[CreateUpdateApiKeyRequestValidatorError, CreateUpdateApiKeyRequestBase] = {
    val notAllowedScopes = createApiKeyRequest.scopes.toSet.diff(apiKeyConfig.allowedScopes)

    Validated
      .condNec(
        notAllowedScopes.isEmpty,
        createApiKeyRequest,
        NotAllowedScopesProvidedError(notAllowedScopes)
      )
  }

  private def validateTimeToLive(
      createApiKeyRequest: CreateUpdateApiKeyRequestBase
  ): ValidatedNec[CreateUpdateApiKeyRequestValidatorError, CreateUpdateApiKeyRequestBase] =
    Validated
      .condNec(
        FiniteDuration(createApiKeyRequest.ttl, ApiKeyExpirationCalculator.ttlTimeUnit) <= apiKeyConfig.ttlMax,
        createApiKeyRequest,
        TtlTooLargeError(createApiKeyRequest.ttl, apiKeyConfig.ttlMax)
      )
}

object CreateUpdateApiKeyRequestValidator {

  sealed abstract class CreateUpdateApiKeyRequestValidatorError(override val message: String) extends CustomError
  object CreateUpdateApiKeyRequestValidatorError {

    case class NotAllowedScopesProvidedError(notAllowedScopes: Set[String])
        extends CreateUpdateApiKeyRequestValidatorError(
          message = s"Provided request contains not allowed scopes: [${notAllowedScopes.mkString("'", "', '", "'")}]."
        )

    case class TtlTooLargeError(ttlRequest: Int, ttlMax: FiniteDuration)
        extends CreateUpdateApiKeyRequestValidatorError(
          message =
            s"Provided request contains time-to-live (ttl) of: $ttlRequest ${ApiKeyExpirationCalculator.ttlTimeUnit.toString.toLowerCase} which is bigger than maximum allowed value of: $ttlMax."
        )
  }

}
