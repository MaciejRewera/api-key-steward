package apikeysteward.repositories

import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate, HashedApiKey}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb, TenantDb}
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

class ApiKeyRepository(
    uuidGenerator: UuidGenerator,
    tenantDb: TenantDb,
    apiKeyDb: ApiKeyDb,
    apiKeyDataDb: ApiKeyDataDb,
    secureHashGenerator: SecureHashGenerator
)(transactor: Transactor[IO]) {

  private val logger: StructuredLogger[doobie.ConnectionIO] = Slf4jLogger.getLogger

  def insert(
      publicTenantId: TenantId,
      apiKey: ApiKey,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyDbId <- uuidGenerator.generateUuid
      apiKeyDataDbId <- uuidGenerator.generateUuid

      result <- insertHashed(publicTenantId, hashedApiKey, apiKeyDbId, apiKeyDataDbId, apiKeyData)
    } yield result

  private def insertHashed(
      publicTenantId: TenantId,
      hashedApiKey: HashedApiKey,
      apiKeyDbId: UUID,
      apiKeyDataDbId: UUID,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    (for {
      tenantDbId <- EitherT
        .fromOptionF(
          tenantDb.getByPublicTenantId(publicTenantId),
          ReferencedTenantDoesNotExistError(publicTenantId)
        )
        .map(_.id)

      _ <- logInfoE("Inserting new API Key...")
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKeyDbId, tenantDbId, hashedApiKey.value)))
        .leftSemiflatTap(e => logger.warn(s"Could not insert API Key because: ${e.message}"))
        .flatTap(_ => logInfoE("Inserted new API Key."))

      apiKeyId = apiKeyEntityRead.id

      _ <- logInfoE(s"Inserting API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]...")
      apiKeyDataEntityRead <- EitherT(
        apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKeyDataDbId, tenantDbId, apiKeyId, apiKeyData))
      )
        .flatTap(_ => logInfoE(s"Inserted API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]."))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def update(publicTenantId: TenantId, apiKeyDataUpdate: ApiKeyDataUpdate): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      _ <- logInfoE[ApiKeyDbError](
        s"Updating API Key Data for key with publicKeyId: [${apiKeyDataUpdate.publicKeyId}]..."
      )
      entityAfterUpdateRead <- EitherT(
        apiKeyDataDb.update(publicTenantId, ApiKeyDataEntity.Update.from(apiKeyDataUpdate))
      )
        .flatTap(_ =>
          logInfoE[ApiKeyDbError](s"Updated API Key Data for key with publicKeyId: [${apiKeyDataUpdate.publicKeyId}].")
        )

      apiKeyData = ApiKeyData.from(entityAfterUpdateRead)
    } yield apiKeyData).value.transact(transactor)

  def get(publicTenantId: TenantId, apiKey: ApiKey): IO[Option[ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyData <- getHashed(publicTenantId, hashedApiKey)
    } yield apiKeyData

  private def getHashed(publicTenantId: TenantId, hashedApiKey: HashedApiKey): IO[Option[ApiKeyData]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(publicTenantId, hashedApiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(publicTenantId, apiKeyEntityRead.id))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def getAllForUser(publicTenantId: TenantId, userId: UserId): IO[List[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- apiKeyDataDb.getByUserId(publicTenantId, userId)
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).transact(transactor).compile.toList

  def get(publicTenantId: TenantId, userId: UserId, publicKeyId: ApiKeyId): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getBy(publicTenantId, userId, publicKeyId))
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def getByPublicKeyId(publicTenantId: TenantId, publicKeyId: ApiKeyId): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByPublicKeyId(publicTenantId, publicKeyId))
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def delete(
      publicTenantId: TenantId,
      userId: UserId,
      publicKeyIdToDelete: ApiKeyId
  ): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDelete <- EitherT {
        apiKeyDataDb
          .getBy(publicTenantId, userId, publicKeyIdToDelete)
          .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(userId, publicKeyIdToDelete)))
      }

      deletionResult <- performDeletion(publicTenantId, apiKeyDataToDelete)
    } yield deletionResult).value.transact(transactor)

  def delete(publicTenantId: TenantId, publicKeyIdToDelete: ApiKeyId): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDelete <- EitherT {
        apiKeyDataDb
          .getByPublicKeyId(publicTenantId, publicKeyIdToDelete)
          .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(publicKeyIdToDelete)))
      }

      deletionResult <- performDeletion(publicTenantId, apiKeyDataToDelete)
    } yield deletionResult).value.transact(transactor)

  private def performDeletion(
      publicTenantId: TenantId,
      apiKeyDataToDelete: ApiKeyDataEntity.Read
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyData] = {

    val publicKeyIdToDelete = UUID.fromString(apiKeyDataToDelete.publicKeyId)
    for {
      _ <- deleteApiKeyData(publicTenantId, publicKeyIdToDelete)
      _ <- deleteApiKey(publicTenantId, apiKeyDataToDelete.apiKeyId, publicKeyIdToDelete)

      res = ApiKeyData.from(apiKeyDataToDelete)
    } yield res
  }

  private def deleteApiKeyData(
      publicTenantId: TenantId,
      publicKeyIdToDelete: ApiKeyId
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyDataEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKeyData for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDataDb.delete(publicTenantId, publicKeyIdToDelete)
      _ <- logger.info(s"Deleted ApiKeyData for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def deleteApiKey(
      publicTenantId: TenantId,
      apiKeyId: UUID,
      publicKeyIdToDelete: ApiKeyId
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKey for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDb.delete(publicTenantId, apiKeyId)
      _ <- logger.info(s"Deleted ApiKey for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def logInfoE[E](message: String): EitherT[doobie.ConnectionIO, E, Unit] = EitherT.right(logger.info(message))
}
