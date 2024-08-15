package apikeysteward.repositories

import apikeysteward.model.{ApiKey, ApiKeyData, HashedApiKey}
import apikeysteward.repositories.db.DbCommons.{ApiKeyDeletionError, ApiKeyInsertionError}
import cats.effect.IO

import java.util.UUID

trait ApiKeyRepository {

  def insert(apiKey: ApiKey, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyData]]

  def delete(userId: String, publicKeyIdToDelete: UUID): IO[Either[ApiKeyDeletionError, ApiKeyData]]

  def get(apiKey: ApiKey): IO[Option[ApiKeyData]]

  def getAll(userId: String): IO[List[ApiKeyData]]

  def get(userId: String, publicKeyId: UUID): IO[Option[ApiKeyData]]

  def getAllUserIds: IO[List[String]]
}
