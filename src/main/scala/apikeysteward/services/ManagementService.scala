package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.{ApiKey, ApiKeyData, CustomError}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.db.DbCommons
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.services.CreateUpdateApiKeyRequestValidator.CreateUpdateApiKeyRequestValidatorError
import apikeysteward.services.ManagementService.ApiKeyCreationError
import apikeysteward.services.ManagementService.ApiKeyCreationError.{InsertionError, ValidationError}
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

import java.time.Clock
import java.util.UUID

class ManagementService(
    createApiKeyRequestValidator: CreateUpdateApiKeyRequestValidator,
    apiKeyGenerator: ApiKeyGenerator,
    apiKeyRepository: ApiKeyRepository
)(implicit clock: Clock)
    extends Logging {

  def createApiKey(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreationError, (ApiKey, ApiKeyData)]] =
    (for {
      validatedRequest <- validateRequest(createApiKeyRequest)
      result <- createApiKeyActionWithRetry(userId, validatedRequest)
    } yield result).value

  private def validateRequest(
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, ValidationError, CreateApiKeyRequest] = EitherT.fromEither[IO](
    createApiKeyRequestValidator
      .validateRequest(createApiKeyRequest)
      .left
      .map(err => ValidationError(err.iterator.toSeq))
  )

  private def createApiKeyActionWithRetry(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, ApiKeyCreationError, (ApiKey, ApiKeyData)] = {

    def isWorthRetrying(err: ApiKeyCreationError): Boolean = err match {
      case InsertionError(_) => true
      case _                 => false
    }

    EitherT {
      Retry
        .retry(maxRetries = 3, isWorthRetrying)(createApiKeyAction(userId, createApiKeyRequest))
        .map(_.asRight)
        .recover { case exc: RetryException[ApiKeyCreationError] => exc.error.asLeft[(ApiKey, ApiKeyData)] }
    }
  }

  private def createApiKeyAction(
      userId: String,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreationError, (ApiKey, ApiKeyData)]] =
    (for {
      _ <- logInfoF("Generating API Key...")
      newApiKey <- EitherT.right(apiKeyGenerator.generateApiKey.flatTap(_ => logger.info("Generated API Key.")))

      _ <- logInfoF("Generating public key ID...")
      publicKeyId <- EitherT.right(IO(UUID.randomUUID()).flatTap(_ => logger.info("Generated public key ID.")))

      apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

      _ <- logInfoF("Inserting API Key into database...")
      insertionResult <- EitherT(insertNewApiKey(newApiKey, apiKeyData))

    } yield newApiKey -> insertionResult).value

  private def insertNewApiKey(newApiKey: ApiKey, apiKeyData: ApiKeyData): IO[Either[InsertionError, ApiKeyData]] =
    for {
      insertionResult <- apiKeyRepository.insert(newApiKey, apiKeyData).flatTap {
        case Right(_) => logger.info(s"Inserted API Key with publicKeyId: [${apiKeyData.publicKeyId}] into database.")
        case Left(e)  => logger.warn(s"Could not insert API Key because: ${e.message}")
      }
      res = insertionResult.left.map(InsertionError)
    } yield res

  def deleteApiKey(userId: String, publicKeyId: UUID): IO[Either[ApiKeyDeletionError, ApiKeyData]] =
    for {
      resE <- apiKeyRepository.delete(userId, publicKeyId).flatTap {
        case Right(_) => logger.info(s"Deleted API Key with publicKeyId: [$publicKeyId] from database.")
        case Left(e)  => logger.warn(s"Could not delete API Key because: ${e.message}")
      }
    } yield resE

  def getAllApiKeysFor(userId: String): IO[List[ApiKeyData]] =
    apiKeyRepository.getAll(userId)

  def getApiKey(userId: String, publicKeyId: UUID): IO[Option[ApiKeyData]] =
    apiKeyRepository.get(userId, publicKeyId)

  def getAllUserIds: IO[List[String]] = apiKeyRepository.getAllUserIds

  private def logInfoF(str: String): EitherT[IO, Nothing, Unit] = EitherT.right(logger.info(str))
}

object ManagementService {

  sealed abstract class ApiKeyCreationError(override val message: String) extends CustomError
  object ApiKeyCreationError {

    case class ValidationError(errors: Seq[CreateUpdateApiKeyRequestValidatorError])
        extends ApiKeyCreationError(
          message = s"Request validation failed because: ${errors.map(_.message).mkString("['", "', '", "']")}."
        )

    case class InsertionError(cause: DbCommons.ApiKeyInsertionError) extends ApiKeyCreationError(cause.message)
  }
}
