package apikeysteward.repositories

import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate, HashedApiKey}
import apikeysteward.model.RepositoryErrors.ApiKeyDeletionError.{ApiKeyDataNotFoundError, GenericApiKeyDeletionError}
import apikeysteward.model.RepositoryErrors.{ApiKeyDeletionError, ApiKeyInsertionError, ApiKeyUpdateError}
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

  def update(apiKeyDataUpdate: ApiKeyDataUpdate): IO[Either[ApiKeyUpdateError, ApiKeyData]] =
    (for {
      _ <- logInfoO(
        s"Updating API Key Data for userId: [${apiKeyDataUpdate.userId}], publicKeyId: [${apiKeyDataUpdate.publicKeyId}]..."
      )
      entityAfterUpdateRead <- OptionT(apiKeyDataDb.update(ApiKeyDataEntity.Update.from(apiKeyDataUpdate)))
        .flatTap(_ =>
          logInfoO(
            s"Updated API Key Data for userId: [${apiKeyDataUpdate.userId}], publicKeyId: [${apiKeyDataUpdate.publicKeyId}]."
          )
        )

      scopes <- OptionT.liftF(getScopes(entityAfterUpdateRead.id))

      apiKeyData = ApiKeyData.from(entityAfterUpdateRead, scopes)
    } yield apiKeyData)
      .toRight(ApiKeyUpdateError.apiKeyDataNotFoundError(apiKeyDataUpdate.userId, apiKeyDataUpdate.publicKeyId))
      .value
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

  private def getScopes(apiKeyDataId: Long): doobie.ConnectionIO[List[ScopeEntity.Read]] =
    for {
      apiKeyDataScopesEntitiesRead <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).compile.toList
      scopes <- scopeDb.getByIds(apiKeyDataScopesEntitiesRead.map(_.scopeId)).compile.toList
    } yield scopes

  def getAllUserIds: IO[List[String]] =
    apiKeyDataDb.getAllUserIds.transact(transactor).compile.toList

  def delete(userId: String, publicKeyIdToDelete: UUID): IO[Either[ApiKeyDeletionError, ApiKeyData]] = {
    for {
      apiKeyDataToDeleteE <- apiKeyDataDb
        .getBy(userId, publicKeyIdToDelete)
        .map(_.toRight(ApiKeyDataNotFoundError(userId, publicKeyIdToDelete).asInstanceOf[ApiKeyDeletionError]))

      apiKeyDataScopesToDeleteE <- apiKeyDataToDeleteE
        .traverse(apiKeyData => apiKeyDataScopesDb.getByApiKeyDataId(apiKeyData.id).compile.toList)

      apiKeyDataToDeleteCombinedE = for {
        apiKeyDataToDelete <- apiKeyDataToDeleteE
        apiKeyDataScopesToDelete <- apiKeyDataScopesToDeleteE
      } yield (apiKeyDataToDelete, apiKeyDataScopesToDelete)

      deletionResult <- apiKeyDataToDeleteCombinedE.flatTraverse {
        case (apiKeyDataToDelete, apiKeyDataScopesToDelete) => delete(apiKeyDataToDelete, apiKeyDataScopesToDelete)
      }

    } yield deletionResult
  }.transact(transactor)

  private def delete(
      apiKeyDataToDelete: ApiKeyDataEntity.Read,
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): doobie.ConnectionIO[Either[ApiKeyDeletionError, ApiKeyData]] = {

    val userId = apiKeyDataToDelete.userId
    val publicKeyIdToDelete = UUID.fromString(apiKeyDataToDelete.publicKeyId)

    (for {
      _ <- deleteApiKeyDataScopes(apiKeyDataScopesToDelete, userId, publicKeyIdToDelete)
      _ <- deleteApiKeyData(userId, publicKeyIdToDelete)
      _ <- deleteApiKey(apiKeyDataToDelete.apiKeyId, userId, publicKeyIdToDelete)

      res <- buildResult(apiKeyDataToDelete, apiKeyDataScopesToDelete)
    } yield res).value
      .map(_.toRight(GenericApiKeyDeletionError(userId, publicKeyIdToDelete)))
  }

  private def deleteApiKeyDataScopes(
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read],
      userId: String,
      publicKeyIdToDelete: UUID
  ): OptionT[doobie.ConnectionIO, List[ApiKeyDataScopesEntity.Read]] = {
    val apiKeyDataIds = apiKeyDataScopesToDelete.map(_.apiKeyDataId).distinct

    val result = if (apiKeyDataIds.nonEmpty) {
      for {
        _ <- logger.info(s"Deleting ApiKeyDataScopes for userId: [$userId], publicKeyId: [$publicKeyIdToDelete]...")

        res <- apiKeyDataIds.flatTraverse(apiKeyDataScopesDb.delete(_).compile.toList).map(Option(_)).handleErrorWith {
          err =>
            logger.warn(s"An exception occurred while deleting ApiKeyDataScopes: ${err.getMessage}") >>
              Option.empty[List[ApiKeyDataScopesEntity.Read]].pure[doobie.ConnectionIO]
        }

        _ <- logger.info(s"Deleted ApiKeyDataScopes for userId: [$userId], publicKeyId: [$publicKeyIdToDelete].")
      } yield res
    } else {
      Option(List.empty[ApiKeyDataScopesEntity.Read]).pure[doobie.ConnectionIO]
    }

    OptionT(result)
  }

  private def deleteApiKeyData(
      userId: String,
      publicKeyIdToDelete: UUID
  ): OptionT[doobie.ConnectionIO, ApiKeyDataEntity.Read] =
    OptionT(for {
      _ <- logger.info(s"Deleting ApiKeyData for userId: [$userId], publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDataDb.delete(userId, publicKeyIdToDelete)
      _ <- logger.info(s"Deleted ApiKeyData for userId: [$userId], publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def deleteApiKey(
      apiKeyId: Long,
      userId: String,
      publicKeyIdToDelete: UUID
  ): OptionT[doobie.ConnectionIO, ApiKeyEntity.Read] =
    OptionT(for {
      _ <- logger.info(s"Deleting ApiKey for userId: [$userId], publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDb.delete(apiKeyId)
      _ <- logger.info(s"Deleted ApiKey for userId: [$userId], publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def buildResult(
      apiKeyDataToDelete: ApiKeyDataEntity.Read,
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): OptionT[doobie.ConnectionIO, ApiKeyData] =
    OptionT(for {
      _ <- logger.info(
        s"Building deleted ApiKeyData to return for userId: [${apiKeyDataToDelete.userId}], publicKeyId: [${apiKeyDataToDelete.publicKeyId}]..."
      )
      scopes <- scopeDb.getByIds(apiKeyDataScopesToDelete.map(_.scopeId)).compile.toList
      apiKeyData = ApiKeyData.from(apiKeyDataToDelete, scopes)
      _ <- logger.info(
        s"Built deleted ApiKeyData to return for userId: [${apiKeyDataToDelete.userId}], publicKeyId: [${apiKeyDataToDelete.publicKeyId}]."
      )
    } yield Option(apiKeyData))

  private def logInfoE[E](message: String): EitherT[doobie.ConnectionIO, E, Unit] = EitherT.right(logger.info(message))

  private def logInfoO(message: String): OptionT[doobie.ConnectionIO, Unit] = OptionT.liftF(logger.info(message))
}
