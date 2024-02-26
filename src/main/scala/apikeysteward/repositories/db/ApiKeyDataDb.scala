package apikeysteward.repositories.db

import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{ApiKeyIdAlreadyExistsError, PublicKeyIdAlreadyExistsError}
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.implicits.{catsSyntaxApplicativeId, none}
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION

import java.sql.SQLException
import java.time.{Clock, Instant}

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

  def getByApiKeyId(apiKeyId: Long): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    Queries.getByApiKeyId(apiKeyId).option

  private object Queries {

    def insertData(apiKeyDataEntityWrite: ApiKeyDataEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key_data(
           api_key_id,
           public_key_id,
           name,
           description,
           user_id,
           expires_at,
           created_at,
           updated_at
         ) VALUES (
            ${apiKeyDataEntityWrite.apiKeyId},
            ${apiKeyDataEntityWrite.publicKeyId},
            ${apiKeyDataEntityWrite.name},
            ${apiKeyDataEntityWrite.description},
            ${apiKeyDataEntityWrite.userId},
            ${apiKeyDataEntityWrite.expiresAt},
            $now,
            $now
         )""".stripMargin.update

    def getByApiKeyId(apiKeyId: Long): doobie.Query0[ApiKeyDataEntity.Read] =
      sql"""SELECT
              id,
              api_key_id,
              public_key_id,
              name,
              description,
              user_id,
              expires_at,
              created_at,
              updated_at
            FROM api_key_data
            WHERE api_key_id = $apiKeyId
           """.stripMargin.query[ApiKeyDataEntity.Read]
  }
}
