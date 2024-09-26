package apikeysteward.repositories.db

import apikeysteward.model.HashedApiKey
import apikeysteward.model.RepositoryErrors.ApiKeyInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyInsertionError.ApiKeyAlreadyExistsError
import apikeysteward.repositories.db.entity.ApiKeyEntity
import doobie.SqlState
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION

import java.time.{Clock, Instant}

class ApiKeyDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(apiKeyEntity: ApiKeyEntity.Write): doobie.ConnectionIO[Either[ApiKeyInsertionError, ApiKeyEntity.Read]] = {
    val now = Instant.now(clock)
    Queries
      .insert(apiKeyEntity, now)
      .withUniqueGeneratedKeys[ApiKeyEntity.Read]("id", "created_at", "updated_at")
      .attemptSomeSqlState[ApiKeyInsertionError] {
        case sqlState: SqlState if sqlState == UNIQUE_VIOLATION => ApiKeyAlreadyExistsError
      }
  }

  def getByApiKey(hashedApiKey: HashedApiKey): doobie.ConnectionIO[Option[ApiKeyEntity.Read]] =
    Queries.getByApiKey(hashedApiKey.value).option

  def delete(id: Long): doobie.ConnectionIO[Option[ApiKeyEntity.Read]] =
    for {
      result <- Queries.getBy(id).option
      n <- Queries.delete(id).run
    } yield if (n > 0) result else None

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
