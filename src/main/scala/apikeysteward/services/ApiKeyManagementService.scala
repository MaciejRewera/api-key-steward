package apikeysteward.services

import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model._
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError
import apikeysteward.model.errors.CommonError.UserDoesNotExistError
import apikeysteward.model.errors.{ApiKeyDbError, CustomError}
import apikeysteward.repositories.{ApiKeyRepository, PermissionRepository, UserRepository}
import apikeysteward.routes.model.admin.apikey.UpdateApiKeyAdminRequest
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{
  ApiKeyCreateErrorImpl,
  InsertionError,
  ValidationError
}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

import java.time.Clock

class ApiKeyManagementService(
    createApiKeyRequestValidator: CreateApiKeyRequestValidator,
    apiKeyGenerator: ApiKeyGenerator,
    uuidGenerator: UuidGenerator,
    apiKeyRepository: ApiKeyRepository,
    userRepository: UserRepository,
    permissionRepository: PermissionRepository
)(implicit clock: Clock)
    extends Logging {

  def createApiKey(
      publicTenantId: TenantId,
      publicUserId: UserId,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[ApiKeyCreateError, (ApiKey, ApiKeyData)]] =
    (for {
      validatedRequest <- validateRequest(publicTenantId, publicUserId, createApiKeyRequest)
      result           <- createApiKeyWithRetry(publicTenantId, publicUserId, validatedRequest)
    } yield result).value

  private def validateRequest(
      publicTenantId: TenantId,
      publicUserId: UserId,
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, ValidationError, CreateApiKeyRequest] = EitherT {
    for {
      validationResultE <- createApiKeyRequestValidator.validateCreateRequest(
        publicTenantId,
        publicUserId,
        createApiKeyRequest
      )
      res = validationResultE.left.map(err => ValidationError(err))
    } yield res
  }

  private def createApiKeyWithRetry(
      publicTenantId: TenantId,
      publicUserId: String,
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
          buildCreateApiKeyAction(publicTenantId, publicUserId, createApiKeyRequest)
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
      _ <- logInfoT("Generating API Key...")
      newApiKey <- EitherT(
        apiKeyGenerator
          .generateApiKey(publicTenantId, createApiKeyRequest.templateId)
          .flatTap(_ => logger.info("Generated API Key."))
      ).leftMap(ApiKeyCreateErrorImpl)

      _           <- logInfoT("Generating public key ID...")
      publicKeyId <- EitherT.right(uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated public key ID.")))

      _ <- logInfoT("Fetching Permissions for new API Key...")
      permissions <- EitherT[IO, ApiKeyCreateError, List[Permission]] {
        permissionRepository
          .getBy(publicTenantId, createApiKeyRequest.permissionIds)
          .map(_.left.map(ApiKeyCreateErrorImpl))
          .flatTap(_ => logger.info("Fetched Permissions for new API Key."))
      }

      apiKeyData = ApiKeyData.from(publicKeyId, userId, createApiKeyRequest, permissions)

      _ <- logInfoT("Inserting API Key into database...")
      insertionResult <- EitherT[IO, ApiKeyCreateError, ApiKeyData] {
        insertNewApiKey(publicTenantId, newApiKey, apiKeyData, createApiKeyRequest.templateId)
      }

    } yield newApiKey -> insertionResult).value

  private def insertNewApiKey(
      publicTenantId: TenantId,
      newApiKey: ApiKey,
      apiKeyData: ApiKeyData,
      publicTemplateId: ApiKeyTemplateId
  ): IO[Either[InsertionError, ApiKeyData]] =
    for {
      insertionResult <- apiKeyRepository.insert(publicTenantId, newApiKey, apiKeyData, publicTemplateId).flatTap {
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
      case Right(_) => logger.info(s"Updated Api Key with publicKeyId: [$publicKeyId].")
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

  private def logInfoT(str: String): EitherT[IO, Nothing, Unit] = EitherT.right(logger.info(str))
}

object ApiKeyManagementService {

  sealed abstract class ApiKeyCreateError(override val message: String) extends CustomError

  object ApiKeyCreateError {

    case class ValidationError(error: CreateApiKeyRequestValidatorError)
        extends ApiKeyCreateError(
          message = s"Request validation failed because: ${error.message}"
        )

    case class InsertionError(cause: ApiKeyDbError) extends ApiKeyCreateError(cause.message)

    case class ApiKeyCreateErrorImpl(cause: CustomError) extends ApiKeyCreateError(cause.message)
  }

}
