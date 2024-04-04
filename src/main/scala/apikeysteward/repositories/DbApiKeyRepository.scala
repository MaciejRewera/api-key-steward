package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.{ApiKeyDataNotFound, CannotDeleteApiKeyDataError}
import apikeysteward.repositories.db.DbCommons.{ApiKeyDeletionError, ApiKeyInsertionError}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyDataScopesEntity, ApiKeyEntity, ScopeEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDataScopesDb, ApiKeyDb, ScopeDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import fs2.Stream

import java.util.UUID

class DbApiKeyRepository(
    apiKeyDb: ApiKeyDb,
    apiKeyDataDb: ApiKeyDataDb,
    scopeDb: ScopeDb,
    apiKeyDataScopesDb: ApiKeyDataScopesDb
)(transactor: Transactor[IO])
    extends ApiKeyRepository[String] {

  override def insert(apiKey: String, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    (for {
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKey)))
      apiKeyId = apiKeyEntityRead.id
      apiKeyDataEntityRead <- EitherT(apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKeyId, apiKeyData)))
      insertedScopes <- EitherT(insertScopes(apiKeyData.scopes.map(ScopeEntity.Write), apiKeyDataEntityRead))

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, insertedScopes)
    } yield apiKeyData).value.transact(transactor)

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

  override def get(apiKey: String): IO[Option[ApiKeyData]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(apiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(apiKeyEntityRead.id))

      scopes <- OptionT(getScopes(apiKeyDataEntityRead.id).some.sequence)

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead, scopes)
    } yield apiKeyData).value.transact(transactor)

  override def getAll(userId: String): IO[List[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- apiKeyDataDb.getByUserId(userId)
      apiKeyData <- Stream.eval(getScopes(apiKeyDataEntityRead.id)).map(ApiKeyData.from(apiKeyDataEntityRead, _))
    } yield apiKeyData).transact(transactor).compile.toList

  private def getScopes(apiKeyDataId: Long): doobie.ConnectionIO[List[ScopeEntity.Read]] =
    for {
      apiKeyDataScopesEntitiesRead <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).compile.toList
      scopes <- scopeDb.getByIds(apiKeyDataScopesEntitiesRead.map(_.scopeId)).compile.toList
    } yield scopes

  override def getAllUserIds: IO[List[String]] =
    apiKeyDataDb.getAllUserIds.transact(transactor).compile.toList

  override def delete(userId: String, publicKeyIdToDelete: UUID): IO[Either[ApiKeyDeletionError, ApiKeyData]] = {
    for {
      apiKeyDataToDeleteE <- apiKeyDataDb
        .getBy(userId, publicKeyIdToDelete)
        .map(_.toRight(ApiKeyDataNotFound(userId, publicKeyIdToDelete).asInstanceOf[ApiKeyDeletionError]))

      apiKeyDataScopesToDeleteE <- apiKeyDataToDeleteE
        .traverse(apiKeyData => apiKeyDataScopesDb.getByApiKeyDataId(apiKeyData.id).compile.toList)

      apiKeyDataToDeleteCombinedE = for {
        apiKeyDataToDelete <- apiKeyDataToDeleteE
        apiKeyDataScopesToDelete <- apiKeyDataScopesToDeleteE
      } yield (apiKeyDataToDelete, apiKeyDataScopesToDelete)

      deletionResult <- apiKeyDataToDeleteCombinedE.flatTraverse {
        case (apiKeyDataToDelete, apiKeyDataScopesToDelete) =>
          delete(userId, publicKeyIdToDelete, apiKeyDataToDelete, apiKeyDataScopesToDelete)
      }

    } yield deletionResult
  }.transact(transactor)

  private def delete(
      userId: String,
      publicKeyIdToDelete: UUID,
      apiKeyDataToDelete: ApiKeyDataEntity.Read,
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): doobie.ConnectionIO[Either[ApiKeyDeletionError, ApiKeyData]] =
    (for {
      _ <- OptionT(apiKeyDataDb.copyIntoDeletedTable(userId, publicKeyIdToDelete))

      _ <- OptionT(apiKeyDataScopesToDelete.traverse { scopeLinkToDelete =>
        apiKeyDataScopesDb.copyIntoDeletedTable(scopeLinkToDelete.apiKeyDataId, scopeLinkToDelete.scopeId)
      }.map(_.sequence))

      _ <- OptionT(apiKeyDataScopesToDelete.traverse { scopeLinkToDelete =>
        apiKeyDataScopesDb.delete(scopeLinkToDelete.apiKeyDataId, scopeLinkToDelete.scopeId)
      }.map(_.sequence))

      _ <- OptionT(apiKeyDataDb.delete(userId, publicKeyIdToDelete))

      _ <- OptionT(apiKeyDb.delete(apiKeyDataToDelete.apiKeyId))

      res <- OptionT(buildResult(apiKeyDataToDelete, apiKeyDataScopesToDelete).map(Option(_)))
    } yield res).value
      .map(_.toRight(CannotDeleteApiKeyDataError(userId, publicKeyIdToDelete)))

  private def buildResult(
      apiKeyDataToDelete: ApiKeyDataEntity.Read,
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): doobie.ConnectionIO[ApiKeyData] =
    for {
      scopes <- scopeDb.getByIds(apiKeyDataScopesToDelete.map(_.scopeId)).compile.toList
      apiKeyData = ApiKeyData.from(apiKeyDataToDelete, scopes)
    } yield apiKeyData

}
