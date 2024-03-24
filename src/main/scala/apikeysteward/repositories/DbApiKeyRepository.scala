package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity, ScopeEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb, ScopeDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class DbApiKeyRepository(apiKeyDb: ApiKeyDb, apiKeyDataDb: ApiKeyDataDb)(
    transactor: Transactor[IO]
) extends ApiKeyRepository[String] {

  override def insert(apiKey: String, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    (for {
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKey)))
      apiKeyId = apiKeyEntityRead.id
      apiKeyDataEntityRead <- EitherT(apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKeyId, apiKeyData)))

//      scopeEntitiesRead <- scopeDb.insertMany(apiKeyData.scopes.map(ScopeEntity.Write(_)))
//      apiKeyDataId = apiKeyDataEntityRead.id
//      _ <-

      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  override def get(apiKey: String): IO[Option[ApiKeyData]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(apiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(apiKeyEntityRead.id))
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  override def getAll(userId: String): IO[List[ApiKeyData]] =
    apiKeyDataDb.getByUserId(userId).map(ApiKeyData.from).transact(transactor).compile.toList

  override def delete(userId: String, publicKeyIdToDelete: UUID): IO[Option[ApiKeyData]] = {
    for {
      apiKeyDataToDeleteOpt <- apiKeyDataDb.getBy(userId, publicKeyIdToDelete)

      deletionResult <- apiKeyDataToDeleteOpt.traverse { apiKeyData =>
        (for {
          _ <- OptionT(apiKeyDataDb.copyIntoDeletedTable(userId, publicKeyIdToDelete).map(booleanToOption))
          _ <- OptionT(apiKeyDataDb.delete(userId, publicKeyIdToDelete).map(booleanToOption))
          _ <- OptionT(apiKeyDb.delete(apiKeyData.apiKeyId).map(booleanToOption))
        } yield ()).value
      }.map(_.flatten)

      apiKeyData = apiKeyDataToDeleteOpt.map(ApiKeyData.from)

    } yield deletionResult.flatMap(_ => apiKeyData)
  }.transact(transactor)

  private def booleanToOption(boolean: Boolean): Option[Boolean] =
    if (boolean) Some(boolean)
    else None

  override def getAllUserIds: IO[List[String]] =
    apiKeyDataDb.getAllUserIds.transact(transactor).compile.toList

}
