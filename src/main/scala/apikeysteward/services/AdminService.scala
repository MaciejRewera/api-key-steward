package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError._
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

    Retry.retry(maxRetries = 3, isWorthRetrying)(createApiKeyAction(userId, createApiKeyRequest))
  }

  private def createApiKeyAction(
      userId: String,
      createApiKeyRequest: CreateApiKeyAdminRequest
  ): IO[Either[ApiKeyInsertionError, (K, ApiKeyData)]] = for {
    newApiKey <- apiKeyGenerator.generateApiKey
    publicKeyId <- IO(UUID.randomUUID())
    apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

    insertionResult <- apiKeyRepository.insert(newApiKey, apiKeyData)

    res = insertionResult.map(newApiKey -> _)
  } yield res

  def deleteApiKey(userId: String, publicKeyId: UUID): IO[Option[ApiKeyData]] =
    apiKeyRepository.delete(userId, publicKeyId)

  def getAllApiKeysFor(userId: String): IO[List[ApiKeyData]] =
    apiKeyRepository.getAll(userId)

  def getAllUserIds: IO[List[String]] = apiKeyRepository.getAllUserIds
}