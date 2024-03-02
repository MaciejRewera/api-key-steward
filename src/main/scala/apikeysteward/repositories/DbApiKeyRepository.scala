package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class DbApiKeyRepository(apiKeyDb: ApiKeyDb, apiKeyDataDb: ApiKeyDataDb, transactor: Transactor[IO])
    extends ApiKeyRepository[String] {

  override def insert(apiKey: String, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyData]] =
    (for {
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKey)))
      apiKey = apiKeyEntityRead.id
      apiKeyDataEntityRead <- EitherT(apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKey, apiKeyData)))
      apiKeyData = ApiKeyData.from(apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  override def get(apiKey: String): IO[Option[ApiKeyDataEntity.Read]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(apiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(apiKeyEntityRead.id))
    } yield apiKeyDataEntityRead).value.transact(transactor)

  override def getAll(userId: String): IO[List[ApiKeyDataEntity.Read]] =
    apiKeyDataDb.getByUserId(userId).transact(transactor).compile.toList

  override def delete(userId: String, publicKeyIdToDelete: UUID): IO[Option[ApiKeyDataEntity.Read]] = {
    for {
      apiKeyDataToDeleteOpt <- apiKeyDataDb.getBy(userId, publicKeyIdToDelete)

      deletionResult <- apiKeyDataToDeleteOpt.traverse { apiKeyData =>
        (for {
          _ <- OptionT(apiKeyDataDb.copyIntoDeletedTable(userId, publicKeyIdToDelete).map(booleanToOption))
          _ <- OptionT(apiKeyDataDb.delete(userId, publicKeyIdToDelete).map(booleanToOption))
          _ <- OptionT(apiKeyDb.delete(apiKeyData.apiKeyId).map(booleanToOption))
        } yield ()).value
      }.map(_.flatten)

    } yield deletionResult.flatMap(_ => apiKeyDataToDeleteOpt)
  }.transact(transactor)

  private def booleanToOption(boolean: Boolean): Option[Boolean] =
    if (boolean) Some(boolean)
    else None

  override def getAllUserIds: IO[List[String]] = ???
}
