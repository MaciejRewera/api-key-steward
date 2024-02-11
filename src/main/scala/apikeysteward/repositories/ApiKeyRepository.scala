package apikeysteward.repositories

import apikeysteward.repositories.entities.ApiKeyDataEntity
import cats.effect.IO

import java.util.UUID

trait ApiKeyRepository[K] {

  def insert(apiKey: K, apiKeyData: ApiKeyDataEntity.Write): IO[ApiKeyDataEntity.Read]

  def delete(userId: String, keyIdToDelete: UUID): IO[Option[ApiKeyDataEntity.Read]]

  def get(apiKey: K): IO[Option[ApiKeyDataEntity.Read]]

  def getAll(userId: String): IO[List[ApiKeyDataEntity.Read]]

  def getAllUserIds: IO[List[String]]
}
