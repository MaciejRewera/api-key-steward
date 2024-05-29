package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository.ApiKeyHash
import apikeysteward.repositories.db.DbCommons.{ApiKeyDeletionError, ApiKeyInsertionError}
import cats.effect.IO

import java.util.UUID

trait ApiKeyRepository {

  def insert(apiKeyHash: ApiKeyHash, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyData]]

  def delete(userId: String, publicKeyIdToDelete: UUID): IO[Either[ApiKeyDeletionError, ApiKeyData]]

  def get(apiKeyHash: ApiKeyHash): IO[Option[ApiKeyData]]

  def getAll(userId: String): IO[List[ApiKeyData]]

  def getAllUserIds: IO[List[String]]
}

object ApiKeyRepository {
  type ApiKeyHash = String
}
