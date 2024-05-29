package apikeysteward.services

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.definitions.ErrorMessages
import cats.effect.IO

class ApiKeyService(apiKeyRepository: ApiKeyRepository) {

  def validateApiKey(apiKey: String): IO[Either[String, ApiKeyData]] =
    for {
      apiKeyDataEntityOptOpt <- apiKeyRepository.get(apiKey)
    } yield apiKeyDataEntityOptOpt.toRight(ErrorMessages.ValidateApiKey.ValidateApiKeyIncorrect)

}
