package apikeysteward.services

import apikeysteward.model.{ApiKey, ApiKeyData}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.definitions.ErrorMessages
import cats.effect.IO

class ApiKeyService(apiKeyRepository: ApiKeyRepository) {

  def validateApiKey(apiKey: ApiKey): IO[Either[String, ApiKeyData]] =
    for {
      apiKeyDataEntityOptOpt <- apiKeyRepository.get(apiKey)
    } yield apiKeyDataEntityOptOpt.toRight(ErrorMessages.ValidateApiKey.ValidateApiKeyIncorrect)

}
