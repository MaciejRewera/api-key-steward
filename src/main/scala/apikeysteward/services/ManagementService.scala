package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.{ApiKey, ApiKeyData}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError._
import apikeysteward.repositories.db.DbCommons.{ApiKeyDeletionError, ApiKeyInsertionError}
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.utils.{Logging, Retry}
import cats.effect.IO

import java.time.Clock
import java.util.UUID

class ManagementService(apiKeyGenerator: ApiKeyGenerator, apiKeyRepository: ApiKeyRepository)(implicit clock: Clock)
    extends Logging {

  def createApiKey(userId: String, createApiKeyRequest: CreateApiKeyRequest): IO[(ApiKey, ApiKeyData)] = {
    def isWorthRetrying(err: ApiKeyInsertionError): Boolean = err match {
      case ApiKeyAlreadyExistsError | PublicKeyIdAlreadyExistsError => true
      case _                                                        => false
    }

    Retry.retry(maxRetries = 3, isWorthRetrying)(createApiKeyAction(userId, createApiKeyRequest))
  }

  private def createApiKeyAction(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyInsertionError, (ApiKey, ApiKeyData)]] = for {

    _ <- logger.info("Generating API Key...")
    newApiKey <- apiKeyGenerator.generateApiKey.flatTap(_ => logger.info("Generated API Key."))

    _ <- logger.info("Generating public key ID...")
    publicKeyId <- IO(UUID.randomUUID()).flatTap(_ => logger.info("Generated public key ID."))

    apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

    _ <- logger.info("Inserting API Key into database...")
    insertionResult <- apiKeyRepository.insert(newApiKey, apiKeyData).flatTap {
      case Right(_) => logger.info("Inserted API Key into database.")
      case Left(e)  => logger.warn(s"Could not insert API Key because: ${e.message}")
    }

    res = insertionResult.map(newApiKey -> _)
  } yield res

  def deleteApiKey(userId: String, publicKeyId: UUID): IO[Either[ApiKeyDeletionError, ApiKeyData]] =
    apiKeyRepository.delete(userId, publicKeyId)

  def getAllApiKeysFor(userId: String): IO[List[ApiKeyData]] =
    apiKeyRepository.getAll(userId)

  def getAllUserIds: IO[List[String]] = apiKeyRepository.getAllUserIds
}
