package apikeysteward.services

import apikeysteward.config.ApiKeyConfig
import apikeysteward.model.errors.CustomError
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError._
import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.implicits.catsSyntaxTuple2Semigroupal

import scala.concurrent.duration.FiniteDuration

class CreateApiKeyRequestValidator(apiKeyConfig: ApiKeyConfig) {

  def validateCreateRequest(
      createApiKeyRequest: CreateApiKeyRequest
  ): Either[NonEmptyChain[CreateApiKeyRequestValidatorError], CreateApiKeyRequest] =
    validateTimeToLive(createApiKeyRequest)
      .map(_ => createApiKeyRequest)
      .toEither

  private def validateTimeToLive(
      createApiKeyRequest: CreateApiKeyRequest
  ): ValidatedNec[CreateApiKeyRequestValidatorError, CreateApiKeyRequest] =
    Validated
      .condNec(
        FiniteDuration(createApiKeyRequest.ttl, ApiKeyExpirationCalculator.TtlTimeUnit) <= apiKeyConfig.ttlMax,
        createApiKeyRequest,
        TtlTooLargeError(createApiKeyRequest.ttl, apiKeyConfig.ttlMax)
      )
}

object CreateApiKeyRequestValidator {

  sealed abstract class CreateApiKeyRequestValidatorError(override val message: String) extends CustomError
  object CreateApiKeyRequestValidatorError {

    case class TtlTooLargeError(ttlRequest: Int, ttlMax: FiniteDuration)
        extends CreateApiKeyRequestValidatorError(
          message =
            s"Provided request contains time-to-live (ttl) of: $ttlRequest ${ApiKeyExpirationCalculator.TtlTimeUnit.toString.toLowerCase} which is bigger than maximum allowed value of: $ttlMax."
        )
  }

}
