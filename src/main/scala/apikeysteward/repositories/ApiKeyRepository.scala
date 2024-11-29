package apikeysteward.repositories

import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError._
import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate, HashedApiKey}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb}
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
    apiKeyDb: ApiKeyDb,
    apiKeyDataDb: ApiKeyDataDb,
    secureHashGenerator: SecureHashGenerator
)(transactor: Transactor[IO]) {

  private val logger: StructuredLogger[doobie.ConnectionIO] = Slf4jLogger.getLogger

  def insert(
      apiKey: ApiKey,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyDbId <- uuidGenerator.generateUuid
      apiKeyDataDbId <- uuidGenerator.generateUuid

      result <- insertHashed(hashedApiKey, apiKeyDbId, apiKeyDataDbId, apiKeyData)
    } yield result

  private def insertHashed(
      hashedApiKey: HashedApiKey,
      apiKeyDbId: UUID,
      apiKeyDataDbId: UUID,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    (for {
      _ <- logInfoE("Inserting new API Key...")
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKeyDbId, hashedApiKey.value)))
        .leftSemiflatTap(e => logger.warn(s"Could not insert API Key because: ${e.message}"))
        .flatTap(_ => logInfoE("Inserted new API Key."))

      apiKeyId = apiKeyEntityRead.id

      _ <- logInfoE(s"Inserting API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]...")
      apiKeyDataEntityRead <- EitherT(
        apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKeyDataDbId, apiKeyId, apiKeyData))
      )
        .flatTap(_ => logInfoE(s"Inserted API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]."))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def update(apiKeyDataUpdate: ApiKeyDataUpdate): IO[Either[ApiKeyDataNotFoundError, ApiKeyData]] =
    (for {
      _ <- logInfoE[ApiKeyDataNotFoundError](
        s"Updating API Key Data for key with publicKeyId: [${apiKeyDataUpdate.publicKeyId}]..."
      )
      entityAfterUpdateRead <- EitherT(apiKeyDataDb.update(ApiKeyDataEntity.Update.from(apiKeyDataUpdate)))
        .flatTap(_ =>
          logInfoE[ApiKeyDataNotFoundError](
            s"Updated API Key Data for key with publicKeyId: [${apiKeyDataUpdate.publicKeyId}]."
          )
        )

      apiKeyData = ApiKeyData.from(entityAfterUpdateRead)
    } yield apiKeyData).value.transact(transactor)

  def get(apiKey: ApiKey): IO[Option[ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyData <- getHashed(hashedApiKey)
    } yield apiKeyData

  private def getHashed(hashedApiKey: HashedApiKey): IO[Option[ApiKeyData]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(hashedApiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(apiKeyEntityRead.id))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def getAllForUser(userId: String): IO[List[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- apiKeyDataDb.getByUserId(userId)
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).transact(transactor).compile.toList

  def get(userId: String, publicKeyId: UUID): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getBy(userId, publicKeyId))
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def getByPublicKeyId(publicKeyId: UUID): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByPublicKeyId(publicKeyId))
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  def delete(userId: String, publicKeyIdToDelete: UUID): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDelete <- EitherT {
        apiKeyDataDb
          .getBy(userId, publicKeyIdToDelete)
          .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(userId, publicKeyIdToDelete)))
      }

      deletionResult <- performDeletion(apiKeyDataToDelete)
    } yield deletionResult).value.transact(transactor)

  def delete(publicKeyIdToDelete: UUID): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDelete <- EitherT {
        apiKeyDataDb
          .getByPublicKeyId(publicKeyIdToDelete)
          .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(publicKeyIdToDelete)))
      }

      deletionResult <- performDeletion(apiKeyDataToDelete)
    } yield deletionResult).value.transact(transactor)

  private def performDeletion(
      apiKeyDataToDelete: ApiKeyDataEntity.Read
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyData] = {

    val publicKeyIdToDelete = UUID.fromString(apiKeyDataToDelete.publicKeyId)
    for {
      _ <- deleteApiKeyData(publicKeyIdToDelete)
      _ <- deleteApiKey(apiKeyDataToDelete.apiKeyId, publicKeyIdToDelete)

      res = ApiKeyData.from(apiKeyDataToDelete)
    } yield res
  }

  private def deleteApiKeyData(
      publicKeyIdToDelete: UUID
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyDataEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKeyData for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDataDb.delete(publicKeyIdToDelete)
      _ <- logger.info(s"Deleted ApiKeyData for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def deleteApiKey(
      apiKeyId: UUID,
      publicKeyIdToDelete: UUID
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKey for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDb.delete(apiKeyId)
      _ <- logger.info(s"Deleted ApiKey for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def logInfoE[E](message: String): EitherT[doobie.ConnectionIO, E, Unit] = EitherT.right(logger.info(message))
}
