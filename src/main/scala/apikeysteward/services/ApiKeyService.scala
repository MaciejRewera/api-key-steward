package apikeysteward.services

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import cats.effect.IO

class ApiKeyService[K](apiKeyRepository: ApiKeyRepository[K]) {

  def validateApiKey(apiKey: K): IO[Either[String, ApiKeyData]] =
    for {
      apiKeyDataEntityOptOpt <- apiKeyRepository.get(apiKey)
      apiKeyDataOpt = apiKeyDataEntityOptOpt.map(ApiKeyData.from)
    } yield apiKeyDataOpt.toRight("Provided API Key is incorrect or does not exist.")

}
