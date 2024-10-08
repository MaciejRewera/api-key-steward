package apikeysteward.repositories.db

import apikeysteward.model.HashedApiKey
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError._
import apikeysteward.repositories.db.entity.ApiKeyEntity
import cats.implicits.toTraverseOps
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION

import java.sql.SQLException
import java.time.{Clock, Instant}

class ApiKeyDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(apiKeyEntity: ApiKeyEntity.Write): doobie.ConnectionIO[Either[ApiKeyInsertionError, ApiKeyEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(apiKeyEntity, now)
        .withUniqueGeneratedKeys[ApiKeyEntity.Read]("id", "created_at", "updated_at")
        .attemptSql

      res = eitherResult.left.map(recoverSqlException)

    } yield res
  }

  private def recoverSqlException(sqlException: SQLException): ApiKeyInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("api_key") => ApiKeyAlreadyExistsError
      case _                                                                     => ApiKeyInsertionErrorImpl(sqlException)
    }

  def getByApiKey(hashedApiKey: HashedApiKey): doobie.ConnectionIO[Option[ApiKeyEntity.Read]] =
    Queries.getByApiKey(hashedApiKey.value).option

  def delete(id: Long): doobie.ConnectionIO[Either[ApiKeyNotFoundError.type, ApiKeyEntity.Read]] =
    for {
      apiKeyToDeleteE <- Queries.getBy(id).option.map(_.toRight(ApiKeyNotFoundError))
      resultE <- apiKeyToDeleteE.traverse(result => Queries.delete(id).run.map(_ => result))
    } yield resultE

  private object Queries {

    def insert(apiKeyEntityWrite: ApiKeyEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key(
           api_key,
           created_at,
           updated_at
         ) VALUES (
            ${apiKeyEntityWrite.apiKey},
            $now,
            $now
         )""".stripMargin.update

    def getByApiKey(apiKey: String): doobie.Query0[ApiKeyEntity.Read] =
      sql"""SELECT id, created_at, updated_at FROM api_key WHERE api_key = $apiKey""".query[ApiKeyEntity.Read]

    def getBy(id: Long): doobie.Query0[ApiKeyEntity.Read] =
      sql"""SELECT id, created_at, updated_at FROM api_key WHERE id = $id""".query[ApiKeyEntity.Read]

    def delete(id: Long): doobie.Update0 =
      sql"""DELETE FROM api_key WHERE api_key.id = $id """.stripMargin.update
  }
}
