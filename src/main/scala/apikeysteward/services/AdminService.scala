package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.routes.model.admin.CreateApiKeyAdminRequest
import cats.effect.IO

import java.time.Clock
import java.util.UUID

class AdminService[K](apiKeyGenerator: ApiKeyGenerator[K], apiKeyRepository: ApiKeyRepository[K])(
    implicit clock: Clock
) {

  def createApiKey(userId: String, createApiKeyRequest: CreateApiKeyAdminRequest): IO[(K, ApiKeyData)] = {
    // TODO: Add maxRetries
    def loop: IO[(K, ApiKeyData)] =
      for {
        newApiKey <- apiKeyGenerator.generateApiKey
        publicKeyId <- IO(UUID.randomUUID())
        apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

        insertionResult <- apiKeyRepository.insert(newApiKey, apiKeyData)

        res <- insertionResult.fold(
          { case ApiKeyAlreadyExistsError | PublicKeyIdAlreadyExistsError => loop },
          apiKeyDataResult => IO(newApiKey -> apiKeyDataResult)
        )

      } yield res

    loop
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
