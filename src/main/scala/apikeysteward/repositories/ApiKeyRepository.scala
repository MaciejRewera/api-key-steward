package apikeysteward.repositories

import apikeysteward.routes.model.ApiKeyData
import cats.effect.IO

trait ApiKeyRepository[K] {

  def insert(apiKey: K, apiKeyData: ApiKeyData): IO[ApiKeyData]

  def get(apiKey: K): IO[Option[ApiKeyData]]

  def getAll(userId: String): IO[List[ApiKeyData]]
}
