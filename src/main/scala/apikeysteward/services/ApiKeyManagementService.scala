package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.{ApiKeyDataNotFoundError, ApiKeyInsertionError}
import apikeysteward.model._
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.model.admin.apikey.UpdateApiKeyAdminRequest
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

import java.time.Clock
import java.util.UUID

class ApiKeyManagementService(
    createApiKeyRequestValidator: CreateApiKeyRequestValidator,
    apiKeyGenerator: ApiKeyGenerator,
    uuidGenerator: UuidGenerator,
    apiKeyRepository: ApiKeyRepository
)(implicit clock: Clock)
    extends Logging {

  def createApiKey(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreateError, (ApiKey, ApiKeyData)]] =
    (for {
      validatedRequest <- validateRequest(createApiKeyRequest)
      result <- createApiKeyWithRetry(userId, validatedRequest)
    } yield result).value

  private def validateRequest(
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, ValidationError, CreateApiKeyRequest] = EitherT.fromEither[IO](
    createApiKeyRequestValidator
      .validateCreateRequest(createApiKeyRequest)
      .left
      .map(err => ValidationError(err.iterator.toSeq))
  )

  private def createApiKeyWithRetry(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, ApiKeyCreateError, (ApiKey, ApiKeyData)] = {

    val createApiKeyMaxRetries = 3
    def isWorthRetrying(err: ApiKeyCreateError): Boolean = err match {
      case InsertionError(_) => true
      case _                 => false
    }

    EitherT {
      Retry
        .retry(maxRetries = createApiKeyMaxRetries, isWorthRetrying)(
          buildCreateApiKeyAction(userId, createApiKeyRequest)
        )
        .map(_.asRight)
        .recover { case exc: RetryException[ApiKeyCreateError] => exc.error.asLeft[(ApiKey, ApiKeyData)] }
    }
  }

  private def buildCreateApiKeyAction(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreateError, (ApiKey, ApiKeyData)]] =
    (for {
      _ <- logInfoF("Generating API Key...")
      newApiKey <- EitherT.right(apiKeyGenerator.generateApiKey.flatTap(_ => logger.info("Generated API Key.")))

      _ <- logInfoF("Generating public key ID...")
      publicKeyId <- EitherT.right(uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated public key ID.")))

      apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

      _ <- logInfoF("Inserting API Key into database...")
      insertionResult <- EitherT(insertNewApiKey(newApiKey, apiKeyData))

    } yield newApiKey -> insertionResult).value

  private def insertNewApiKey(newApiKey: ApiKey, apiKeyData: ApiKeyData): IO[Either[InsertionError, ApiKeyData]] =
    for {
      insertionResult <- apiKeyRepository.insert(newApiKey, apiKeyData).flatTap {
        case Right(_) => logger.info(s"Inserted API Key with publicKeyId: [${apiKeyData.publicKeyId}] into database.")
        case Left(e)  => logger.warn(s"Could not insert API Key into database because: ${e.message}")
      }
      res = insertionResult.left.map(InsertionError)
    } yield res

  def updateApiKey(
      publicKeyId: UUID,
      updateApiKeyRequest: UpdateApiKeyAdminRequest
  ): IO[Either[ApiKeyDataNotFoundError, ApiKeyData]] =
    apiKeyRepository.update(ApiKeyDataUpdate.from(publicKeyId, updateApiKeyRequest)).flatTap {
      case Right(_) => logger.info(s"Updated Api Key with publicKeyId: [${publicKeyId}].")
      case Left(e)  => logger.warn(s"Could not update Api Key with publicKeyId: [$publicKeyId] because: ${e.message}")
    }

  def deleteApiKeyBelongingToUserWith(userId: String, publicKeyId: UUID): IO[Either[ApiKeyDbError, ApiKeyData]] =
    for {
      resE <- apiKeyRepository.delete(userId, publicKeyId).flatTap {
        case Right(_) => logger.info(s"Deleted API Key with publicKeyId: [$publicKeyId] from database.")
        case Left(e)  => logger.warn(s"Could not delete API Key with publicKeyId: [$publicKeyId] because: ${e.message}")
      }
    } yield resE

  def deleteApiKey(publicKeyId: UUID): IO[Either[ApiKeyDbError, ApiKeyData]] =
    for {
      resE <- apiKeyRepository.delete(publicKeyId).flatTap {
        case Right(_) => logger.info(s"Deleted API Key with publicKeyId: [$publicKeyId] from database.")
        case Left(e)  => logger.warn(s"Could not delete API Key with publicKeyId: [$publicKeyId] because: ${e.message}")
      }
    } yield resE

  def getAllApiKeysFor(userId: String): IO[List[ApiKeyData]] =
    apiKeyRepository.getAll(userId)

  def getApiKey(userId: String, publicKeyId: UUID): IO[Option[ApiKeyData]] =
    apiKeyRepository.get(userId, publicKeyId)

  def getAllUserIds: IO[List[String]] = apiKeyRepository.getAllUserIds

  private def logInfoF(str: String): EitherT[IO, Nothing, Unit] = EitherT.right(logger.info(str))
}

object ApiKeyManagementService {

  sealed abstract class ApiKeyCreateError(override val message: String) extends CustomError
  object ApiKeyCreateError {

    case class ValidationError(errors: Seq[CreateApiKeyRequestValidatorError])
        extends ApiKeyCreateError(
          message = s"Request validation failed because: ${errors.map(_.message).mkString("['", "', '", "']")}."
        )

    case class InsertionError(cause: ApiKeyInsertionError) extends ApiKeyCreateError(cause.message)
  }
}
