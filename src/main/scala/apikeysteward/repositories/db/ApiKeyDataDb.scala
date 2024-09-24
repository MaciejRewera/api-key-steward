package apikeysteward.repositories.db

import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyIdAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.implicits.catsSyntaxApplicativeId
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION
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

      res = eitherResult.left.map(recoverSqlException)

    } yield res
  }

  private def recoverSqlException(sqlException: SQLException): ApiKeyInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("api_key_id")    => ApiKeyIdAlreadyExistsError
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_key_id") => PublicKeyIdAlreadyExistsError
    }

  def update(apiKeyDataEntity: ApiKeyDataEntity.Write): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      updateCount <- Queries.update(apiKeyDataEntity, now).run

      res <-
        if (updateCount > 0) getBy(apiKeyDataEntity.userId, apiKeyDataEntity.publicKeyId)
        else Option.empty[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]
    } yield res
  }

  def getByApiKeyId(apiKeyId: Long): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    Queries.getByApiKeyId(apiKeyId).option

  def getByUserId(userId: String): Stream[doobie.ConnectionIO, ApiKeyDataEntity.Read] =
    Queries.getByUserId(userId).stream

  def getBy(userId: String, publicKeyId: UUID): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    getBy(userId, publicKeyId.toString)

  private def getBy(userId: String, publicKeyId: String): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    Queries.getBy(userId, publicKeyId).option

  def getAllUserIds: Stream[doobie.ConnectionIO, String] =
    Queries.getAllUserIds.stream

  def delete(userId: String, publicKeyId: UUID): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    for {
      result <- getBy(userId, publicKeyId)
      n <- Queries.delete(userId, publicKeyId.toString).run
    } yield if (n > 0) result else None

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

    def update(apiKeyDataEntityWrite: ApiKeyDataEntity.Write, now: Instant): doobie.Update0 =
      sql"""UPDATE api_key_data
            SET name = ${apiKeyDataEntityWrite.name},
                description = ${apiKeyDataEntityWrite.description},
                expires_at = ${apiKeyDataEntityWrite.expiresAt},
                updated_at = $now
            WHERE api_key_data.user_id = ${apiKeyDataEntityWrite.userId} AND api_key_data.public_key_id = ${apiKeyDataEntityWrite.publicKeyId}
           """.stripMargin.update

    def getByApiKeyId(apiKeyId: Long): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE api_key_id = $apiKeyId").query[ApiKeyDataEntity.Read]

    def getByUserId(userId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE user_id = $userId").query[ApiKeyDataEntity.Read]

    def getBy(userId: String, publicKeyId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM api_key_data WHERE user_id = $userId AND public_key_id = $publicKeyId").query[ApiKeyDataEntity.Read]

    val getAllUserIds: doobie.Query0[String] =
      sql"SELECT DISTINCT user_id FROM api_key_data".query[String]

    def delete(userId: String, publicKeyId: String): doobie.Update0 =
      sql"""DELETE FROM api_key_data
            WHERE api_key_data.user_id = $userId AND api_key_data.public_key_id = $publicKeyId
           """.stripMargin.update

  }
}
