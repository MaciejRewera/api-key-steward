package apikeysteward.services

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.definitions.Endpoints.ErrorMessages
import cats.effect.IO

class ApiKeyService[K](apiKeyRepository: ApiKeyRepository[K]) {

  def validateApiKey(apiKey: K): IO[Either[String, ApiKeyData]] =
    for {
      apiKeyDataEntityOptOpt <- apiKeyRepository.get(apiKey)
    } yield apiKeyDataEntityOptOpt.toRight(ErrorMessages.ValidateApiKeyIncorrect)

}
