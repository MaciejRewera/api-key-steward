package apikeysteward.repositories

import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError._
import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate, HashedApiKey}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyDataScopesEntity, ApiKeyEntity, ScopeEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDataScopesDb, ApiKeyDb, ScopeDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import fs2.Stream
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

class ApiKeyRepository(
    apiKeyDb: ApiKeyDb,
    apiKeyDataDb: ApiKeyDataDb,
    scopeDb: ScopeDb,
    apiKeyDataScopesDb: ApiKeyDataScopesDb,
    secureHashGenerator: SecureHashGenerator
)(transactor: Transactor[IO]) {

  private val logger: StructuredLogger[doobie.ConnectionIO] = Slf4jLogger.getLogger

  def insert(
      apiKey: ApiKey,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyData <- insertHashed(hashedApiKey, apiKeyData)
    } yield apiKeyData

  private def insertHashed(
      hashedApiKey: HashedApiKey,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    (for {
      _ <- logInfoE("Inserting new API Key...")
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(hashedApiKey.value)))
        .leftSemiflatTap(e => logger.warn(s"Could not insert API Key because: ${e.message}"))
        .flatTap(_ => logInfoE("Inserted new API Key."))

      apiKeyId = apiKeyEntityRead.id

      _ <- logInfoE(s"Inserting API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]...")
      apiKeyDataEntityRead <- EitherT(apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKeyId, apiKeyData)))
        .flatTap(_ => logInfoE(s"Inserted API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]."))

      _ <- logInfoE(s"Inserting scopes for API Key with publicKeyId: [${apiKeyData.publicKeyId}]...")
      insertedScopes <- EitherT(insertScopes(apiKeyData.scopes.map(ScopeEntity.Write), apiKeyDataEntityRead))
        .flatTap(_ => logInfoE(s"Inserted scopes for API Key with publicKeyId: [${apiKeyData.publicKeyId}]."))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, insertedScopes)
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

      scopes <- EitherT.right[ApiKeyDataNotFoundError](getScopes(entityAfterUpdateRead.id))

      apiKeyData = ApiKeyData.from(entityAfterUpdateRead, scopes)
    } yield apiKeyData).value
      .transact(transactor)

  private def insertScopes(
      scopes: List[ScopeEntity.Write],
      apiKeyDataEntityRead: ApiKeyDataEntity.Read
  ): doobie.ConnectionIO[Either[ApiKeyInsertionError, List[ScopeEntity.Read]]] =
    for {
      scopeEntitiesRead <- scopeDb.insertMany(scopes).compile.toList

      apiKeyDataScopesEntities = scopeEntitiesRead.map(scopeEntityRead =>
        ApiKeyDataScopesEntity.Write(apiKeyDataEntityRead.id, scopeEntityRead.id)
      )
      _ <- apiKeyDataScopesDb.insertMany(apiKeyDataScopesEntities)
    } yield Right(scopeEntitiesRead)

  def get(apiKey: ApiKey): IO[Option[ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyData <- getHashed(hashedApiKey)
    } yield apiKeyData

  private def getHashed(hashedApiKey: HashedApiKey): IO[Option[ApiKeyData]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(hashedApiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(apiKeyEntityRead.id))
      scopes <- OptionT(getScopes(apiKeyDataEntityRead.id).some.sequence)

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, scopes)
    } yield apiKeyData).value.transact(transactor)

  def getAll(userId: String): IO[List[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- apiKeyDataDb.getByUserId(userId)
      scopes <- Stream.eval(getScopes(apiKeyDataEntityRead.id))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, scopes)
    } yield apiKeyData).transact(transactor).compile.toList

  def get(userId: String, publicKeyId: UUID): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getBy(userId, publicKeyId))
      scopes <- OptionT(getScopes(apiKeyDataEntityRead.id).some.sequence)

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, scopes)
    } yield apiKeyData).value.transact(transactor)

  def getByPublicKeyId(publicKeyId: UUID): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByPublicKeyId(publicKeyId))
      scopes <- OptionT(getScopes(apiKeyDataEntityRead.id).some.sequence)

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, scopes)
    } yield apiKeyData).value.transact(transactor)

  private def getScopes(apiKeyDataId: Long): doobie.ConnectionIO[List[ScopeEntity.Read]] =
    for {
      apiKeyDataScopesEntitiesRead <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).compile.toList
      scopes <- scopeDb.getByIds(apiKeyDataScopesEntitiesRead.map(_.scopeId)).compile.toList
    } yield scopes

  def getAllUserIds: IO[List[String]] =
    apiKeyDataDb.getAllUserIds.transact(transactor).compile.toList

  def delete(userId: String, publicKeyIdToDelete: UUID): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDeleteE <- apiKeyDataDb
        .getBy(userId, publicKeyIdToDelete)
        .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(userId, publicKeyIdToDelete)))

      deletionResult <- delete(apiKeyDataToDeleteE)
    } yield deletionResult).transact(transactor)

  def delete(publicKeyIdToDelete: UUID): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDeleteE <- apiKeyDataDb
        .getByPublicKeyId(publicKeyIdToDelete)
        .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(publicKeyIdToDelete)))

      deletionResult <- delete(apiKeyDataToDeleteE)
    } yield deletionResult).transact(transactor)

  private def delete(
      apiKeyDataToDeleteE: Either[ApiKeyDbError, ApiKeyDataEntity.Read]
  ): doobie.ConnectionIO[Either[ApiKeyDbError, ApiKeyData]] =
    for {
      apiKeyDataScopesToDeleteE <- apiKeyDataToDeleteE
        .traverse(apiKeyData => apiKeyDataScopesDb.getByApiKeyDataId(apiKeyData.id).compile.toList)

      apiKeyDataToDeleteCombinedE = for {
        apiKeyDataToDelete <- apiKeyDataToDeleteE
        apiKeyDataScopesToDelete <- apiKeyDataScopesToDeleteE
      } yield (apiKeyDataToDelete, apiKeyDataScopesToDelete)

      deletionResult <- apiKeyDataToDeleteCombinedE.flatTraverse {
        case (apiKeyDataToDelete, apiKeyDataScopesToDelete) =>
          performDeletion(apiKeyDataToDelete, apiKeyDataScopesToDelete)
      }
    } yield deletionResult

  private def performDeletion(
      apiKeyDataToDelete: ApiKeyDataEntity.Read,
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): doobie.ConnectionIO[Either[ApiKeyDbError, ApiKeyData]] = {

    val publicKeyIdToDelete = UUID.fromString(apiKeyDataToDelete.publicKeyId)

    (for {
      _ <- deleteApiKeyDataScopes(apiKeyDataScopesToDelete, publicKeyIdToDelete)
      _ <- deleteApiKeyData(publicKeyIdToDelete)
      _ <- deleteApiKey(apiKeyDataToDelete.apiKeyId, publicKeyIdToDelete)

      res <- buildResult(apiKeyDataToDelete, apiKeyDataScopesToDelete)
    } yield res).value
  }

  private def deleteApiKeyDataScopes(
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read],
      publicKeyIdToDelete: UUID
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, List[ApiKeyDataScopesEntity.Read]] = {
    val apiKeyDataIds = apiKeyDataScopesToDelete.map(_.apiKeyDataId).distinct

    val result = if (apiKeyDataIds.nonEmpty) {
      for {
        _ <- logger.info(s"Deleting ApiKeyDataScopes for key with publicKeyId: [$publicKeyIdToDelete]...")

        res <- apiKeyDataIds
          .flatTraverse(apiKeyDataScopesDb.delete(_).compile.toList)
          .map(_.asRight[ApiKeyDbError])

        _ <- logger.info(s"Deleted ApiKeyDataScopes for key with publicKeyId: [$publicKeyIdToDelete].")
      } yield res
    } else {
      List.empty[ApiKeyDataScopesEntity.Read].asRight[ApiKeyDbError].pure[doobie.ConnectionIO]
    }

    EitherT(result)
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
      apiKeyId: Long,
      publicKeyIdToDelete: UUID
  ): EitherT[doobie.ConnectionIO, ApiKeyNotFoundError.type, ApiKeyEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKey for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDb.delete(apiKeyId)
      _ <- logger.info(s"Deleted ApiKey for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def buildResult(
      apiKeyDataToDelete: ApiKeyDataEntity.Read,
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyData] =
    EitherT {
      for {
        _ <- logger.info(
          s"Building deleted ApiKeyData to return for userId: [${apiKeyDataToDelete.userId}], publicKeyId: [${apiKeyDataToDelete.publicKeyId}]..."
        )
        scopes <- scopeDb.getByIds(apiKeyDataScopesToDelete.map(_.scopeId)).compile.toList
        apiKeyData = ApiKeyData.from(apiKeyDataToDelete, scopes)
        _ <- logger.info(
          s"Built deleted ApiKeyData to return for userId: [${apiKeyDataToDelete.userId}], publicKeyId: [${apiKeyDataToDelete.publicKeyId}]."
        )
      } yield Right(apiKeyData)
    }

  private def logInfoE[E](message: String): EitherT[doobie.ConnectionIO, E, Unit] = EitherT.right(logger.info(message))
}
