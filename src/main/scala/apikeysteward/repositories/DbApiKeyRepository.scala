package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
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

  override def delete(userId: String, publicKeyIdToDelete: UUID): IO[Option[ApiKeyData]] = {
    for {
      apiKeyDataToDeleteOpt <- apiKeyDataDb.getBy(userId, publicKeyIdToDelete)

      apiKeyDataScopesToDelete <- apiKeyDataToDeleteOpt
        .traverse(apiKeyData => apiKeyDataScopesDb.getByApiKeyDataId(apiKeyData.id))
        .compile
        .toList
        .map(_.flatten)

      deletionResult <- apiKeyDataToDeleteOpt.traverse { apiKeyData =>
        (for {
          _ <- OptionT(apiKeyDataDb.copyIntoDeletedTable(userId, publicKeyIdToDelete).map(booleanToOption))
          _ <- OptionT(apiKeyDataScopesToDelete.traverse { entityToDelete =>
            apiKeyDataScopesDb
              .copyIntoDeletedTable(entityToDelete.apiKeyDataId, entityToDelete.scopeId)
          }.map(areAllSuccessful))

          _ <- OptionT(apiKeyDataScopesToDelete.traverse { entityToDelete =>
            apiKeyDataScopesDb
              .delete(entityToDelete.apiKeyDataId, entityToDelete.scopeId)
          }.map(areAllSuccessful))

          _ <- OptionT(apiKeyDataDb.delete(userId, publicKeyIdToDelete).map(booleanToOption))
          _ <- OptionT(apiKeyDb.delete(apiKeyData.apiKeyId).map(booleanToOption))
        } yield ()).value
      }.map(_.flatten)

      result <- deletionResult
        .traverse(_ => buildResult(apiKeyDataToDeleteOpt, apiKeyDataScopesToDelete))
        .map(_.flatten)
    } yield result
  }.transact(transactor)

  private def booleanToOption(boolean: Boolean): Option[Boolean] =
    if (boolean) Some(boolean)
    else None

  private def areAllSuccessful(list: List[Boolean]): Option[Boolean] = booleanToOption(list.forall(_ == true))

  private def buildResult(
      apiKeyDataToDeleteOpt: Option[ApiKeyDataEntity.Read],
      apiKeyDataScopesToDelete: List[ApiKeyDataScopesEntity.Read]
  ): doobie.ConnectionIO[Option[ApiKeyData]] =
    for {
      scopes <- scopeDb.getByIds(apiKeyDataScopesToDelete.map(_.scopeId)).compile.toList
      apiKeyData = apiKeyDataToDeleteOpt.map(ApiKeyData.from(_, scopes))
    } yield apiKeyData

}
