package apikeysteward.repositories

import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb, ClientUsersDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class DbApiKeyRepository(apiKeyDb: ApiKeyDb, apiKeyDataDb: ApiKeyDataDb, clientUsersDb: ClientUsersDb)(
    transactor: Transactor[IO]
) extends ApiKeyRepository[String] {

  override def insert(apiKey: String, apiKeyData: ApiKeyData): IO[Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]] =
    (for {
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKey)))
      apiKeyId = apiKeyEntityRead.id
      apiKeyDataEntityRead <- EitherT(apiKeyDataDb.insert(ApiKeyDataEntity.Write.from(apiKeyId, apiKeyData)))
    } yield apiKeyDataEntityRead).value.transact(transactor)

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

  override def getAllUserIds(clientId: String): IO[List[String]] =
    clientUsersDb.getAllByClientId(clientId).transact(transactor).map(_.userId).compile.toList
}
