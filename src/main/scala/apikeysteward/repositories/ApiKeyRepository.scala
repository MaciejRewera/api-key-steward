package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.effect.IO

import java.util.UUID

trait ApiKeyRepository[K] {

  def insert(apiKey: K, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyData]]

  def delete(userId: String, publicKeyIdToDelete: UUID): IO[Option[ApiKeyData]]

  def get(apiKey: K): IO[Option[ApiKeyData]]

  def getAll(userId: String): IO[List[ApiKeyData]]

  def getAllUserIds(clientId: String): IO[List[String]]
}
