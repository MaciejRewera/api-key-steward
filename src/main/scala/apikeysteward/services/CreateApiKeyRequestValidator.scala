package apikeysteward.services

import apikeysteward.config.ApiKeyConfig
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.NotAllowedScopesProvidedError
import cats.implicits.catsSyntaxEitherId

class CreateApiKeyRequestValidator(apiKeyConfig: ApiKeyConfig) {

  def validateRequest(
      createApiKeyRequest: CreateApiKeyRequest
  ): Either[CreateApiKeyRequestValidatorError, CreateApiKeyRequest] = {
    val notAllowedScopes = createApiKeyRequest.scopes.toSet.diff(apiKeyConfig.allowedScopes)

    if (notAllowedScopes.isEmpty)
      createApiKeyRequest.asRight
    else
      NotAllowedScopesProvidedError(notAllowedScopes).asLeft
  }
}

object CreateApiKeyRequestValidator {

  sealed abstract class CreateApiKeyRequestValidatorError(val message: String)
  object CreateApiKeyRequestValidatorError {

    case class NotAllowedScopesProvidedError(notAllowedScopes: Set[String])
        extends CreateApiKeyRequestValidatorError(
          message = s"Provided request contains not allowed scopes: [${notAllowedScopes.mkString("'", "', '", "'")}]."
        )
  }

}
