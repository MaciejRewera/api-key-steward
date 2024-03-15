package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.routes.model.admin.CreateApiKeyAdminRequest
import apikeysteward.utils.Retry
import cats.effect.IO

import java.time.Clock
import java.util.UUID

class AdminService[K](apiKeyGenerator: ApiKeyGenerator[K], apiKeyRepository: ApiKeyRepository[K])(
    implicit clock: Clock
) {

  def createApiKey(userId: String, createApiKeyRequest: CreateApiKeyAdminRequest): IO[(K, ApiKeyData)] = {
    def isWorthRetrying(err: ApiKeyInsertionError): Boolean = err match {
      case ApiKeyAlreadyExistsError | PublicKeyIdAlreadyExistsError => true
      case _                                                        => false
    }

    Retry.retry(maxRetries = 3, isWorthRetrying) {
      for {
        newApiKey <- apiKeyGenerator.generateApiKey
        publicKeyId <- IO(UUID.randomUUID())
        apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

        insertionResult <- apiKeyRepository.insert(newApiKey, apiKeyData)

        res = insertionResult.map(newApiKey -> ApiKeyData.from(_))
      } yield res
    }
  }

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

  def getAllUserIds(clientId: String): IO[List[String]] = apiKeyRepository.getAllUserIds(clientId)
}
