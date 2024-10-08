package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.{ApiKeyDataNotFoundError, ApiKeyInsertionError}
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class ApiKeyDataDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(
      apiKeyDataEntity: ApiKeyDataEntity.Write
  ): doobie.ConnectionIO[Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insertData(apiKeyDataEntity, now)
        .withUniqueGeneratedKeys[ApiKeyDataEntity.Read](
          "id",
          "api_key_id",
          "public_key_id",
          "name",
          "description",
          "user_id",
          "expires_at",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, apiKeyDataEntity.apiKeyId))

    } yield res
  }

  private def recoverSqlException(sqlException: SQLException, apiKeyId: Long): ApiKeyInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("api_key_id")    => ApiKeyIdAlreadyExistsError
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_key_id") => PublicKeyIdAlreadyExistsError
      case FOREIGN_KEY_VIOLATION.value                                                 => ReferencedApiKeyDoesNotExistError(apiKeyId)
      case _                                                                           => ApiKeyInsertionErrorImpl(sqlException)
    }

  def update(
      apiKeyDataEntity: ApiKeyDataEntity.Update
  ): doobie.ConnectionIO[Either[ApiKeyDataNotFoundError, ApiKeyDataEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      updateCount <- Queries.update(apiKeyDataEntity, now).run

      resOpt <-
        if (updateCount > 0) getByPublicKeyId(apiKeyDataEntity.publicKeyId)
        else Option.empty[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(
      ApiKeyDataNotFoundError(apiKeyDataEntity.publicKeyId)
    )
  }

  def getByApiKeyId(apiKeyId: Long): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    Queries.getByApiKeyId(apiKeyId).option

  def getByUserId(userId: String): Stream[doobie.ConnectionIO, ApiKeyDataEntity.Read] =
    Queries.getByUserId(userId).stream

  def getByPublicKeyId(publicKeyId: UUID): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    getByPublicKeyId(publicKeyId.toString)

  private def getByPublicKeyId(publicKeyId: String): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    Queries.getByPublicKeyId(publicKeyId).option

  def getBy(userId: String, publicKeyId: UUID): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    getBy(userId, publicKeyId.toString)

  private def getBy(userId: String, publicKeyId: String): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    Queries.getBy(userId, publicKeyId).option

  def getAllUserIds: Stream[doobie.ConnectionIO, String] =
    Queries.getAllUserIds.stream

  def delete(publicKeyId: UUID): doobie.ConnectionIO[Either[ApiKeyDbError, ApiKeyDataEntity.Read]] =
    for {
      apiKeyToDeleteE <- getByPublicKeyId(publicKeyId).map(_.toRight(ApiKeyDataNotFoundError(publicKeyId)))
      resultE <- apiKeyToDeleteE.traverse(result => Queries.delete(publicKeyId.toString).run.map(_ => result))
    } yield resultE

  private object Queries {

    private val columnNamesInsertFragment =
      fr"INSERT INTO api_key_data(api_key_id, public_key_id, name, description, user_id, expires_at, created_at, updated_at)"

    private val columnNamesSelectFragment =
      fr"SELECT id, api_key_id, public_key_id, name, description, user_id, expires_at, created_at, updated_at"

    def insertData(apiKeyDataEntityWrite: ApiKeyDataEntity.Write, now: Instant): doobie.Update0 =
      (columnNamesInsertFragment ++
        sql"""VALUES (
            ${apiKeyDataEntityWrite.apiKeyId},
            ${apiKeyDataEntityWrite.publicKeyId},
            ${apiKeyDataEntityWrite.name},
            ${apiKeyDataEntityWrite.description},
            ${apiKeyDataEntityWrite.userId},
            ${apiKeyDataEntityWrite.expiresAt},
            $now,
            $now
         )""".stripMargin).update

    def update(apiKeyDataEntityUpdate: ApiKeyDataEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE api_key_data
            SET name = ${apiKeyDataEntityUpdate.name},
                description = ${apiKeyDataEntityUpdate.description},
                updated_at = $now
            WHERE api_key_data.public_key_id = ${apiKeyDataEntityUpdate.publicKeyId}
           """.stripMargin.update

    def getByApiKeyId(apiKeyId: Long): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE api_key_id = $apiKeyId").query[ApiKeyDataEntity.Read]

    def getByUserId(userId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE user_id = $userId").query[ApiKeyDataEntity.Read]

    def getByPublicKeyId(publicKeyId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE public_key_id = $publicKeyId").query[ApiKeyDataEntity.Read]

    def getBy(userId: String, publicKeyId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE user_id = $userId AND public_key_id = $publicKeyId").query[ApiKeyDataEntity.Read]

    val getAllUserIds: doobie.Query0[String] =
      sql"SELECT DISTINCT user_id FROM api_key_data".query[String]

    def delete(publicKeyId: String): doobie.Update0 =
      sql"""DELETE FROM api_key_data
            WHERE api_key_data.public_key_id = $publicKeyId
           """.stripMargin.update

  }
}
