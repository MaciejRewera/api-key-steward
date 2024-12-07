package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError
import apikeysteward.model.RepositoryErrors.GenericError.UserDoesNotExistError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model._
import apikeysteward.repositories.{ApiKeyRepository, UserRepository}
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
    apiKeyRepository: ApiKeyRepository,
    userRepository: UserRepository
)(implicit clock: Clock)
    extends Logging {

  def createApiKey(
      publicTenantId: TenantId,
      userId: UserId,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreateError, (ApiKey, ApiKeyData)]] =
    (for {
      validatedRequest <- validateRequest(createApiKeyRequest)
      result <- createApiKeyWithRetry(publicTenantId, userId, validatedRequest)
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
      publicTenantId: TenantId,
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
          buildCreateApiKeyAction(publicTenantId, userId, createApiKeyRequest)
        )
        .map(_.asRight)
        .recover { case exc: RetryException[ApiKeyCreateError] => exc.error.asLeft[(ApiKey, ApiKeyData)] }
    }
  }

  private def buildCreateApiKeyAction(
      publicTenantId: TenantId,
      userId: UserId,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreateError, (ApiKey, ApiKeyData)]] =
    (for {
      _ <- logInfoF("Generating API Key...")
      newApiKey <- EitherT.right(apiKeyGenerator.generateApiKey.flatTap(_ => logger.info("Generated API Key.")))

      _ <- logInfoF("Generating public key ID...")
      publicKeyId <- EitherT.right(uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated public key ID.")))

      apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest)

      _ <- logInfoF("Inserting API Key into database...")
      insertionResult <- EitherT(insertNewApiKey(publicTenantId, newApiKey, apiKeyData))

    } yield newApiKey -> insertionResult).value

  private def insertNewApiKey(
      publicTenantId: TenantId,
      newApiKey: ApiKey,
      apiKeyData: ApiKeyData
  ): IO[Either[InsertionError, ApiKeyData]] =
    for {
      insertionResult <- apiKeyRepository.insert(publicTenantId, newApiKey, apiKeyData).flatTap {
        case Right(_) => logger.info(s"Inserted API Key with publicKeyId: [${apiKeyData.publicKeyId}] into database.")
        case Left(e)  => logger.warn(s"Could not insert API Key into database because: ${e.message}")
      }
      res = insertionResult.left.map(InsertionError)
    } yield res

  def updateApiKey(
      publicTenantId: TenantId,
      publicKeyId: ApiKeyId,
      updateApiKeyRequest: UpdateApiKeyAdminRequest
  ): IO[Either[ApiKeyDbError, ApiKeyData]] =
    apiKeyRepository.update(publicTenantId, ApiKeyDataUpdate.from(publicKeyId, updateApiKeyRequest)).flatTap {
      case Right(_) => logger.info(s"Updated Api Key with publicKeyId: [${publicKeyId}].")
      case Left(e)  => logger.warn(s"Could not update Api Key with publicKeyId: [$publicKeyId] because: ${e.message}")
    }

  def deleteApiKeyBelongingToUserWith(
      publicTenantId: TenantId,
      userId: UserId,
      publicKeyId: ApiKeyId
  ): IO[Either[ApiKeyDbError, ApiKeyData]] =
    delete(publicKeyId)(apiKeyRepository.delete(publicTenantId, userId, publicKeyId))

  def deleteApiKey(publicTenantId: TenantId, publicKeyId: ApiKeyId): IO[Either[ApiKeyDbError, ApiKeyData]] =
    delete(publicKeyId)(apiKeyRepository.delete(publicTenantId, publicKeyId))

  private def delete(
      publicKeyId: ApiKeyId
  )(deleteFunction: => IO[Either[ApiKeyDbError, ApiKeyData]]): IO[Either[ApiKeyDbError, ApiKeyData]] =
    for {
      resE <- deleteFunction.flatTap {
        case Right(_) => logger.info(s"Deleted API Key with publicKeyId: [$publicKeyId] from database.")
        case Left(e)  => logger.warn(s"Could not delete API Key with publicKeyId: [$publicKeyId] because: ${e.message}")
      }
    } yield resE

  def getAllForUser(publicTenantId: TenantId, userId: UserId): IO[Either[UserDoesNotExistError, List[ApiKeyData]]] =
    (for {
      _ <- EitherT(
        userRepository.getBy(publicTenantId, userId).map(_.toRight(UserDoesNotExistError(publicTenantId, userId)))
      )

      result <- EitherT.liftF[IO, UserDoesNotExistError, List[ApiKeyData]](
        apiKeyRepository.getAllForUser(publicTenantId, userId)
      )
    } yield result).value

  def getApiKey(publicTenantId: TenantId, userId: UserId, publicKeyId: ApiKeyId): IO[Option[ApiKeyData]] =
    apiKeyRepository.get(publicTenantId, userId, publicKeyId)

  def getApiKey(publicTenantId: TenantId, publicKeyId: ApiKeyId): IO[Option[ApiKeyData]] =
    apiKeyRepository.getByPublicKeyId(publicTenantId, publicKeyId)

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
