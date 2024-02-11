package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.entities.ApiKeyDataEntity
import apikeysteward.routes.model.admin.CreateApiKeyAdminRequest
import cats.effect.IO

import java.util.UUID

class AdminService[K](apiKeyGenerator: ApiKeyGenerator[K], apiKeyRepository: ApiKeyRepository[K]) {

  def createApiKey(createApiKeyRequest: CreateApiKeyAdminRequest): IO[(K, ApiKeyData)] =
    for {
      newApiKey <- apiKeyGenerator.generateApiKey
      keyId <- IO(UUID.randomUUID())
      apiKeyDataEntityWrite = ApiKeyDataEntity.Write.from(createApiKeyRequest, keyId)

      apiKeyDataEntityRead <- apiKeyRepository.insert(newApiKey, apiKeyDataEntityWrite)
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield (newApiKey, apiKeyData)

  def deleteApiKey(userId: String, keyId: UUID): IO[Option[ApiKeyData]] =
    for {
      deletedApiKeyDataEntity <- apiKeyRepository.delete(userId, keyId)
      deletedApiKeyData = deletedApiKeyDataEntity.map(ApiKeyData.from)
    } yield deletedApiKeyData

  def getAllApiKeysFor(userId: String): IO[List[ApiKeyData]] =
    for {
      apiKeyDataEntities <- apiKeyRepository.getAll(userId)
      result = apiKeyDataEntities.map(ApiKeyData.from)
    } yield result

  def getAllUserIds: IO[List[String]] = apiKeyRepository.getAllUserIds
}
