package apikeysteward.repositories

import apikeysteward.repositories.entities.ApiKeyDataEntity
import cats.effect.IO

import java.time.Instant
import java.util.UUID
import scala.collection.mutable.{Map => MMap}

class InMemoryApiKeyRepository[K] extends ApiKeyRepository[K] {

  private val apiKeysTable: MMap[K, UUID] = MMap.empty
  private val apiKeyDataTable: MMap[UUID, ApiKeyDataEntity.Read] = MMap.empty

  override def insert(apiKey: K, apiKeyDataEntityWrite: ApiKeyDataEntity.Write): IO[ApiKeyDataEntity.Read] = IO {
    apiKeysTable.put(apiKey, apiKeyDataEntityWrite.keyId)

    val now = Instant.now()
    val entityRead = ApiKeyDataEntity.Read(
      userId = apiKeyDataEntityWrite.userId,
      keyId = apiKeyDataEntityWrite.keyId,
      name = apiKeyDataEntityWrite.name,
      description = apiKeyDataEntityWrite.description,
      scope = apiKeyDataEntityWrite.scope,
      createdAt = now,
      expiresAt = now.plusMillis(apiKeyDataEntityWrite.ttl)
    )
    apiKeyDataTable.put(apiKeyDataEntityWrite.keyId, entityRead)

    entityRead
  }

  override def get(apiKey: K): IO[Option[ApiKeyDataEntity.Read]] = IO {
    for {
      apiKeyId <- apiKeysTable.get(apiKey)
      apiKeyData <- apiKeyDataTable.get(apiKeyId)
    } yield apiKeyData
  }

  override def getAll(userId: String): IO[List[ApiKeyDataEntity.Read]] = ???
}
