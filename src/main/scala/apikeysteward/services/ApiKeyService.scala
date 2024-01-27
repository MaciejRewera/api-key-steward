package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.routes.model.ApiKeyData
import cats.effect.IO
import cats.effect.std.Random

class ApiKeyService[T](apiKeyGenerator: ApiKeyGenerator[T]) {

  def createApiKey(apiKeyData: ApiKeyData): IO[T] = apiKeyGenerator.generateApiKey

  def validateApiKey(apiKey: T): IO[Either[String, ApiKeyData]] =
    Random.scalaUtilRandom[IO].flatMap(_.nextDouble).map { rnd =>
      if (rnd > 0.5)
        Right(ApiKeyData(userId = "at-some-point-this-will-be-a-valid-user-id", apiKeyName = "valid-api-key-name"))
      else
        Left("Provided API Key is incorrect or does not exist.")
    }

}
