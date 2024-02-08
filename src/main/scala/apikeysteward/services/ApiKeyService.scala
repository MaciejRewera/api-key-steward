package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.model.ApiKeyData
import cats.effect.IO

class ApiKeyService[K](apiKeyGenerator: ApiKeyGenerator[K], apiKeyRepository: ApiKeyRepository[K]) {

  def createApiKey(apiKeyData: ApiKeyData): IO[(K, ApiKeyData)] =
    for {
      newApiKey <- apiKeyGenerator.generateApiKey
      apiKeyData <- apiKeyRepository.insert(newApiKey, apiKeyData)
    } yield (newApiKey, apiKeyData)

  def validateApiKey(apiKey: K): IO[Either[String, ApiKeyData]] =
    for {
      apiKeyDataOpt <- apiKeyRepository.get(apiKey)
    } yield apiKeyDataOpt.toRight("Provided API Key is incorrect or does not exist.")

}
