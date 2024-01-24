package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.routes.model.CreateApiKeyRequest
import cats.effect.IO

class ApiKeyCreationService[T](apiKeyGenerator: ApiKeyGenerator[T]) {

  private val defaultApiKeyLength = 50

  def createApiKey(request: CreateApiKeyRequest): IO[T] = apiKeyGenerator.generateApiKey(defaultApiKeyLength)
}
