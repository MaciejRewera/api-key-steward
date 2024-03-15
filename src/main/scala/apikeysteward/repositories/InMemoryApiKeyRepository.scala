package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.effect.IO

import java.util.UUID
import scala.collection.mutable.{Map => MMap}

class InMemoryApiKeyRepository[K] extends ApiKeyRepository[K] {

  private val apiKeysTable: MMap[K, UUID] = MMap.empty
  private val apiKeyDataTable: MMap[UUID, ApiKeyDataEntity.Read] = MMap.empty

  override def insert(
      apiKey: K,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] = IO {
//    apiKeysTable.put(apiKey, apiKeyDataEntityWrite.keyId)
//
//    val now = Instant.now()
//    val entityRead = ApiKeyDataEntity.Read(
//      userId = apiKeyDataEntityWrite.userId,
//      keyId = apiKeyDataEntityWrite.keyId,
//      name = apiKeyDataEntityWrite.name,
//      description = apiKeyDataEntityWrite.description,
//      scope = apiKeyDataEntityWrite.scope,
//      createdAt = now,
//      expiresAt = now.plusMillis(apiKeyDataEntityWrite.ttl)
//    )
//    apiKeyDataTable.put(apiKeyDataEntityWrite.keyId, entityRead)
//
//    entityRead
    ???
  }

  override def delete(userId: String, keyIdToDelete: UUID): IO[Option[ApiKeyData]] = IO {
    apiKeyDataTable.find { case (keyId, entity) => keyId == keyIdToDelete && entity.userId == userId }
      .flatMap(_ => apiKeyDataTable.remove(keyIdToDelete).map(ApiKeyData.from))
  }

  override def get(apiKey: K): IO[Option[ApiKeyData]] = IO {
    for {
      apiKeyId <- apiKeysTable.get(apiKey)
      apiKeyData <- apiKeyDataTable.get(apiKeyId).map(ApiKeyData.from)
    } yield apiKeyData
  }

  override def getAll(userId: String): IO[List[ApiKeyData]] = IO {
    apiKeyDataTable.values.filter(_.userId == userId).map(ApiKeyData.from).toList
  }

  override def getAllUserIds(clientId: String): IO[List[String]] = IO {
    apiKeyDataTable.values.map(_.userId).toList.distinct
  }
}
