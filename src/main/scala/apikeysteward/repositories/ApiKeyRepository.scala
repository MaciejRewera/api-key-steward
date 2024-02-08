package apikeysteward.repositories

import apikeysteward.repositories.entities.ApiKeyDataEntity
import cats.effect.IO

trait ApiKeyRepository[K] {

  def insert(apiKey: K, apiKeyData: ApiKeyDataEntity.Write): IO[ApiKeyDataEntity.Read]

  def get(apiKey: K): IO[Option[ApiKeyDataEntity.Read]]

  def getAll(userId: String): IO[List[ApiKeyDataEntity.Read]]

  def getAllUserIds: IO[List[String]]
}
