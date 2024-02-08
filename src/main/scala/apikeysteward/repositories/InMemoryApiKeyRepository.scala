package apikeysteward.repositories

import apikeysteward.routes.model.ApiKeyData
import cats.effect.IO
import scala.collection.mutable.{Map => MMap}

import java.util.UUID

class InMemoryApiKeyRepository[K] extends ApiKeyRepository[K] {

  private val apiKeysTable: MMap[K, UUID] = MMap.empty
  private val apiKeyDataTable: MMap[UUID, ApiKeyData] = MMap.empty

  // TODO: Should return error if given API Key exists.
  //       Should also not allow for 2 API Keys with the same name.
  override def insert(apiKey: K, apiKeyData: ApiKeyData): IO[ApiKeyData] = IO {
    val apiKeyId = UUID.randomUUID()
    apiKeysTable.put(apiKey, apiKeyId)
    apiKeyDataTable.put(apiKeyId, apiKeyData)

    apiKeyData
  }

  override def get(apiKey: K): IO[Option[ApiKeyData]] = IO {
    for {
      apiKeyId <- apiKeysTable.get(apiKey)
      apiKeyData <- apiKeyDataTable.get(apiKeyId)
    } yield apiKeyData
  }

  override def getAll(userId: String): IO[List[ApiKeyData]] = ???
}
